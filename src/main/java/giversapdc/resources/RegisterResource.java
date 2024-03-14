package giversapdc.resources;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.EntityQuery;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.PathElement;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StringValue;
import com.google.cloud.datastore.Transaction;
import com.google.cloud.datastore.Value;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.gson.Gson;

import giversapdc.util.AuthToken;
import giversapdc.util.CacheSupport;
import giversapdc.util.EventData;
//import giversapdc.util.GroupData;
import giversapdc.util.InstitutionData;
import giversapdc.util.MapMarker;
import giversapdc.util.RegisterData;
import giversapdc.util.ShopItemData;

@Path("/register")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RegisterResource {
	
	private static final String USER = "USER";
	private static final String INST_OWNER = "INST_OWNER";
	//private static final String GROUP_OWNER = "GROUP_OWNER";
	private static final String SU = "SU";
	private static final String SUPPORT_EMAIL = "support@givers-volunteering.appspotmail.com";
	private static final String VERIFY_URL = "https://givers-volunteering.appspot.com/verify";
	
	private static final String DEFAULT_USER_IMG = "https://storage.googleapis.com/givers-volunteering.appspot.com/defaultImages/userDefault.jpg";
	private static final String DEFAULT_INST_IMG = "https://storage.googleapis.com/givers-volunteering.appspot.com/defaultImages/instDefault.jpg";
	private static final String DEFAULT_EVENT_IMG = "https://storage.googleapis.com/givers-volunteering.appspot.com/defaultImages/eventDefault.jpg";
	private static final String DEFAULT_SHOP_IMG = "https://storage.googleapis.com/givers-volunteering.appspot.com/defaultImages/shopDefault.png";
	private static final String INST_BADGE = "https://storage.googleapis.com/givers-volunteering.appspot.com/badges/institution.png";
	
	private static final String PROJECT_ID = "givers-volunteering";
	private static final String BUCKET_ID = "givers-volunteering.appspot.com";
	private static final String BUCKET_URL = "https://storage.googleapis.com/givers-volunteering.appspot.com/";
	private static final String SHOP_ITEMS = "shopItems/";
	private static final String EVENTS = "events/";
	private static final String INSTITUTIONS = "institutions/";
		
	private static final String PNG = "image/png";
	private static final String JPEG = "image/jpeg";
	
	private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
	private final Gson g = new Gson();
	
	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
	private KeyFactory profileKeyFactory = datastore.newKeyFactory().setKind("Profile");
	private KeyFactory authTokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");
	private KeyFactory institutionKeyFactory =  datastore.newKeyFactory().setKind("Institution");
	private KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind("Event");
	//private KeyFactory groupKeyFactory = datastore.newKeyFactory().setKind("Group");
	private KeyFactory markerKeyFactory = datastore.newKeyFactory().setKind("Marker");
	private KeyFactory shopItemKeyFactory = datastore.newKeyFactory().setKind("Shop");
	private KeyFactory registerVerifKeyFactory = datastore.newKeyFactory().setKind("RegisterVerification");
	private KeyFactory logKeyFactory = datastore.newKeyFactory().setKind("Log");
	private KeyFactory rbacKeyFactory = datastore.newKeyFactory().setKind("AccessControl");
	
	private CacheSupport cacheSupport = new CacheSupport();
	
	public RegisterResource() { }
	
	
	/**
	 * Used to send a verification email, with a confirmation link, to a given username
	 * @param txn - active transaction from where the method was called
	 * @param username - username of the account to verify
	 * @param to - mail of the account to verify
	 * @param code - verification code needed, 5 minute limit
	 * @return response message with status
	 */
	private Response sendVerificationMail(Transaction txn, String username, String to, String link) {
		//Configure properties
		Properties props = new Properties();
		
		//Account from where to send email
		String from = SUPPORT_EMAIL;
		
		//Create session
		Session session = Session.getInstance(props, null);
		
		try {
			//Build message
			String msg = "<p style=\"font-family: Arial\">Olá " + username + "!</p>\r\n"
					+ "<br>\r\n"
					+ "<p style=\"font-family: Arial\">Obrigado por se ter juntado ao Givers Volunteering. Para terminar o registo, siga <a href=" + link + ">este link</a> para validar a sua conta.</p>\r\n"
					+ "<br>\r\n"
					+ "<p style=\"font-family: Arial\">Caso não tenha utilizado este endereço para se registar no Givers Volunteering, pode simplesmente ignorar este email. "
					+ "Pedimos desculpa pelo incómodo.</p>\r\n"
					+ "<br>\r\n"
					+ "<p style=\"font-family: Arial\">Obrigado,</p>\r\n"
					+ "<p style=\"font-family: Arial\">Equipa NovaWare</p>";
			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(from));
			message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
			message.setSubject("Givers Volunteering Support - Registo");
			message.setContent(msg, "text/html");

			//Send mail
			Transport.send(message);
			
			LOG.info("Verification mail sent successfully.");
			return Response.ok().build();
		} catch( Exception e ) {			
			txn.rollback();
			LOG.severe("Failed to send account verification mail: " + e.getMessage());
			return Response.status(Status.BAD_REQUEST).entity("Erro no envio de mail de ativação de conta." ).build();
		}
	}
	
	
	/**
	 * Method used to register a new user in the system. By default, accounts will be inactive until they 
	 * verify the account, through the mail sent to them.
	 * The mail is sent through SMTP and the verification is done through another rest call in class OperationsResource
	 * @param data - username, email, name, password and passwordConfirm
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/user")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerUser(RegisterData data) { 
		LOG.info("Attempt to register user.");
		
		Transaction txn = datastore.newTransaction();

		try {
			//Check for user login
			if( data.at != null ) {
				LOG.warning("Attempt to register new account while user logged in.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Deve realizar o logout antes de registar outra conta.").build();
			}

			//Check input data
			Response r = data.validRegisterData(txn);
			if( r.getStatus() != 200 ) {
				LOG.warning("Attempt to register new account with invalid data.");
				txn.rollback();
				return r;
			}

			String username = data.username.replaceAll("\\s+", "").toLowerCase();
			Key userKey = userKeyFactory.newKey(username);
			Key profileKey = profileKeyFactory.addAncestors(PathElement.of("User", username)).newKey(username);
			Entity user = txn.get(userKey);
			Entity profile = txn.get(profileKey);
			
			//Check for user in the database.
			if( user != null ) {
				LOG.warning("User already exists on register attempt.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("User já existe.").build();
			}
			
			//Check if given mail already has an account associated. Set limit to 1 to try and minimise the querying
			EntityQuery query = Query.newEntityQueryBuilder()
					.setKind("User")
					.setFilter(PropertyFilter.eq("email", data.email.toLowerCase()))
					.setLimit(1)
					.build();
	
			QueryResults<Entity> tasks = txn.run(query);
			
			if( tasks.hasNext() ) {
				LOG.warning("Register attempt with mail that already has an account associated.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Já existe uma conta registada com o mail indicado.").build();
			}
			
			//Creates a user with main attributes, and blank profile, which can be edited later		
			//Default role is USER, account state is INACTIVE (state = false), lists are empty and points == 0
			//To activate account, user must confirm email, by clicking the link sent to their register mail
			user = Entity.newBuilder(userKey)
					.set("username", data.username)
					.set("password", DigestUtils.sha512Hex(data.password))
					.set("email", data.email.toLowerCase())
					.set("name", data.name)
					.set("role", USER)
					.set("state", false)
					.set("creation_time", System.currentTimeMillis())
					.set("shopPoints", 0)
					.set("scorePoints", 0)
					.set("inEvent", "")
					.build();

			//Blank profile
			profile = Entity.newBuilder(profileKey)
					.set("interests", new ArrayList<Value<String>>())
					.set("phoneNr", "")
					.set("dateOfBirth", 0)
					.set("gender", "")
					.set("nationality", "")
					.set("address", "")
					.set("description", "")
					.set("photo", DEFAULT_USER_IMG)
					.set("badges", new ArrayList<Value<String>>())
					.build();	
			
			//Create random 6 digit verification code
			Random rnd = new Random();
			int nrRnd = 100000 + rnd.nextInt(900000);
			
			//Add verification code to database. Will get deleted as soon as user performs "validateAccount"
			Key reigsterVerifKey = registerVerifKeyFactory.newKey(username);
			Entity registerVerif = Entity.newBuilder(reigsterVerifKey)
					.set("username", username)
					.set("code", String.valueOf(nrRnd))
					.build();
			
			//Create the link for user verification
			String link = "\"" + VERIFY_URL + "?u=" + username + "&c=" + nrRnd + "\"";
			
			//Send email for account activation
			r = sendVerificationMail(txn, username, data.email, link);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Add verification to database
			txn.put(registerVerif);
			
			//Add user and profile to database and cache
			cacheSupport.cache.put(username, user);
			cacheSupport.cache.put(username + "profile", profile);
			
			txn.add(user, profile);
			
			//Add log of register to database
			r = addLog(txn, "registerUser", username, username);
			if( r.getStatus() != 200 ) {
				LOG.warning("Error on adding database log.");
				txn.rollback();
				return r;
			}
				
			LOG.info("User registered successfully with username " + data.username);
			txn.commit();
			return Response.ok().entity("Registo realizado com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if( txn.isActive() )
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used by a user to register a new institution to the system.
	 * Maximum allowed institutions is one per user (they become "INST_OWNER" role, which can't register institutions)
	 * @param data - name, email, phoneNr, address, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/institution")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerInsitution(InstitutionData data) {
		LOG.info("Attempt to register new institution.");

		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);
			
			//Check login
			Response r = checkLogin(txn, data.at, authToken);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			//Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "registerInstitution");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			Key userKey = userKeyFactory.newKey(authToken.getString("username").toLowerCase());
			Entity user = txn.get(userKey);
			
			//Check if user is in event
			if( !user.getString("inEvent").equals("") ) {
				LOG.warning("Attempt to register institution while in event.");
				txn.rollback();
				return Response.status(Status.BAD_GATEWAY).entity("Não pode criar uma instituição enquanto está a participar num evento.").build();
			}
			
			//Check given data validity
			Response re = data.validDataRegister(txn);
			if( re.getStatus() != 200 ) {
				LOG.warning("Invalid given data on institution register. " + re.getEntity());
				txn.rollback();
				return re;
			}
			
			String instName = data.name.replaceAll("\\s+", "");		
			Key institutionKey = institutionKeyFactory.newKey(instName.toLowerCase());
			Entity institution = txn.get(institutionKey);
			
			//Check if institution exists in the database
			if( institution != null ) {
				LOG.warning("Institution already exists with given name on register institution.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Já existe uma instituição com o mesmo nome.").build();
			}
			
			//Create a new institution and add to database. Person registering becomes the owner/representative
			institution = Entity.newBuilder(institutionKey)
					.set("owner", authToken.getString("username"))
					.set("name", data.name)
					.set("email", data.email)
					.set("phoneNr", data.phoneNr)
					.set("address", data.address)
					.set("photo", DEFAULT_INST_IMG)
					.build();
			
			//If a photo was added as well, upload and edit photo property
			//Photo should never come as null, because web sends empty array, but just in case, check
			if( data.photo == null ) {
				LOG.warning("Attempt to register institution with null photo.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Foto não pode ser null.").build();
			}
			else if( data.photo.length != 0 ) {
				//Upload photo to cloud storage
				String fileName = INSTITUTIONS + instName.toLowerCase() + System.currentTimeMillis();
				String fileLink = BUCKET_URL + fileName;
									
				r = uploadPhoto(txn, fileName, data.photo);
				if( r.getStatus() != 200 )
					return r;

				institution = Entity.newBuilder(institution)
						.set("photo", fileLink)
						.build();
			}
			
			//If role is SU, stays SU. Otherwise change role to INST_OWNER
			if( !user.getString("role").equals(SU) ) {
				user = Entity.newBuilder(user)
						.set("role", INST_OWNER)
						.build();
				
				//Get user's auth tokens
				Query<Entity> query = Query.newEntityQueryBuilder()
						.setKind("AuthToken")
						.setFilter(PropertyFilter.eq("username", authToken.getString("username")))
						.build();
				QueryResults<Entity> tasks = txn.run(query);
				
				//Change role on all of user's active tokens
				tasks.forEachRemaining(autTok -> { 	
					autTok = Entity.newBuilder(autTok)
							.set("role", INST_OWNER)
							.build();
					txn.put(autTok);
					cacheSupport.cache.put(autTok.getKey().getName(), autTok);
				});
			}
			
			//If user didn't have the badge before, add INST badge
			String username = authToken.getString("username");
			Key profileKey = profileKeyFactory.addAncestors(PathElement.of("User", username)).newKey(username);
			Entity profile = txn.get(profileKey);
			List<Value<String>> badges = profile.getList("badges");
					
			if( !badges.contains(StringValue.of(INST_BADGE)) ) {
				profile = Entity.newBuilder(profile)
						.set("badges", addStringToListValuesString(profile.getList("badges"), INST_BADGE))
						.build();
				
				//Update cache and database
				cacheSupport.cache.put(username + "profile", profile);
				txn.put(profile);
			}
			
			//Remove user from all future events that he was participating in
			//Query to get all participating events of user
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("UserEventsJoined")
					.setFilter(PropertyFilter.eq("username", username))
					.build();
	
			//Run query
			QueryResults<Entity> tasks = datastore.run(query);
		
			//Delete participations from all future events, and change said events' capacity
			tasks.forEachRemaining(eventJoin ->{
				Key eventKey = eventKeyFactory.newKey(eventJoin.getString("eventId"));
				Entity event = txn.get(eventKey);
				
				//Remove only if future
				if( event.getLong("date_start") > System.currentTimeMillis() ) {
					event = Entity.newBuilder(event)
							.set("joinedUsersCount", event.getLong("joinedUsersCount") - 1)
							.build();
					
					cacheSupport.cache.put(event.getKey().getName(), event);
					
					txn.put(event);
					txn.delete(eventJoin.getKey());
				}
			});
			
			//Add institution to database and update user
			txn.add(institution);
			txn.put(user);
			
			cacheSupport.cache.put(username, user);
			cacheSupport.cache.put(instName.toLowerCase(), institution);
			
			//Add log of register to database
			r = addLog(txn, "registerInstitution", authToken.getString("username"), instName);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Create new token to send back to user for hiding/showing display purposes
			AuthToken at = new AuthToken(authToken.getKey().getName(), authToken.getString("username"), INST_OWNER, authToken.getLong("expirationDate"));

			LOG.info("Institution register successful " + data.name);
			txn.commit();
			
			return Response.ok(g.toJson(at)).build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if( txn.isActive() )
				txn.rollback();
		}
	}
		
	
	/**
	 * Method used to register an event by an institution owner. The event becomes associated to his institution.
	 * @param data - name, interests, address, dateStart, duration, 
	 * 				capacity, description, markers, at
	 * @return response message with status
	 */
	@POST
	@Path("/event")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerEvent(EventData data) {
		LOG.info("Attempt to register new event.");
		
		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);
			
			//Check login
			Response r = checkLogin(txn, data.at, authToken);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "registerEvent");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check data validity
			Response re = data.validDataRegister();
			if( re.getStatus() != 200 ) {
				LOG.warning("Invalid given data on event register. " + re.getEntity());
				txn.rollback();
				return re;
			}
			
			//Check if event name contains special characters
			if( data.name.contains("?") ) {
				LOG.warning("Attempt to register event with ? in name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento não pode conter \"?\".").build();
			}
			
			//Get user's institution
			EntityQuery query = Query.newEntityQueryBuilder()
					.setKind("Institution")
					.setFilter(PropertyFilter.eq("owner", authToken.getString("username")))
					.build();

			//Run query and get institution
			QueryResults<Entity> tasks = datastore.run(query);
			
			//Check if institution exists in database
			Entity inst = null;
			if( tasks.hasNext() )
				inst = tasks.next();
			else
				return Response.status(Status.NOT_FOUND).entity("Não é representante de nenhuma instituição.").build();

			String eventName = data.name.replaceAll("\\s+", "");
			
			/*
			String nfdNormalizedString = Normalizer.normalize(data.name, Normalizer.Form.NFD); 
		    Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
		    String eventName = pattern.matcher(nfdNormalizedString).replaceAll("");
			*/
			
			Key eventKey = eventKeyFactory.newKey(eventName.toLowerCase());
			Entity event = txn.get(eventKey);

			//Check if event with given name already exists in the database
			if( event != null ) {
				LOG.warning("Event with given name already exists on register, name: " + eventName);
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Já existe um evento com o mesmo nome.").build();
			}	
			
			//Verification code that users will require to start an event
			Random rnd = new Random();
			int nrRnd = 100000 + rnd.nextInt(900000);
			
			//Parse time, comes in as String of this format HH:MM
			int hours = Integer.parseInt(data.duration.split(":")[0]);
			int minutes = Integer.parseInt(data.duration.split(":")[1]);
			Long duration = (long) ((hours*60 + minutes) * 60 * 1000);
			
			//Create event with given data and an empty list of participants
			event = Entity.newBuilder(eventKey)
					.set("institution", inst.getKey().getName())
					.set("name", data.name)
					.set("interests", data.interests.replaceAll("\\s+", "").toLowerCase())
					.set("address", data.address)
					.set("lat", data.markers[0].lat)
					.set("lon", data.markers[0].lng)
					.set("date_start", data.dateStart)
					.set("date_end", data.dateStart + duration)
					.set("capacity", data.capacity)
					.set("joinedUsersCount", 0)
					.set("description", data.description)
					.set("start_code", String.valueOf(nrRnd))
					.set("totalRating", 0.0)
					.set("nrOfRatings", 0)
					.set("actualRating", 0.0)
					.set("photo", DEFAULT_EVENT_IMG)
					.set("photos", new ArrayList<Value<String>>())
					.build();
			
			//If a photo was added as well, upload and edit photo property
			//Photo should never come as null, because web sends empty array, but just in case, check
			if( data.photo == null ) {
				LOG.warning("Attempt to register event with null photo.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Foto não pode ser null.").build();
			}
			else if( data.photo.length != 0 ) {
				//Upload photo to cloud storage
				String fileName = EVENTS + eventName.toLowerCase() + "/" + eventName.toLowerCase() + System.currentTimeMillis();
				String fileLink = BUCKET_URL + fileName;
									
				r = uploadPhoto(txn, fileName, data.photo);
				if( r.getStatus() != 200 )
					return r;

				event = Entity.newBuilder(event)
						.set("photo", fileLink)
						.build();
			}
				
			//Add all map markers
			KeyFactory mkKeyFactory = markerKeyFactory.addAncestor(PathElement.of("Event", eventName.toLowerCase()));
			Key markerKey = null;;
			Entity marker = null;
			
			int i = 0;
			for( MapMarker mrkr : data.markers ) {
				if( i < 10 )
					markerKey = mkKeyFactory.newKey(eventName.toLowerCase() + "000" + i);
				else if( i >= 10 && i < 100 )
					markerKey = mkKeyFactory.newKey(eventName.toLowerCase() + "00" + i);
				else if( i >= 100 && i < 1000 )
					markerKey = mkKeyFactory.newKey(eventName.toLowerCase() + "0" + i);
				else 
					markerKey = mkKeyFactory.newKey(eventName.toLowerCase() + "" + i);
					
				marker = Entity.newBuilder(markerKey)
						.set("lat", mrkr.lat)
						.set("lon", mrkr.lng)
						.set("eventName", data.name)
						.set("eventId", eventName.toLowerCase())
						.set("description", mrkr.description != null ? mrkr.description : "")
						.set("photos", new ArrayList<Value<String>>())
						.set("risk", mrkr.risk)
						.build();
				txn.put(marker);
				i++;
			}

			//Add event to database
			txn.add(event);
			
			//Add log of register to database
			r = addLog(txn, "registerEvent", authToken.getString("username"), eventName);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			txn.commit();
			LOG.info("Event registered successfully with id " + eventName);
			return Response.ok().entity("Evento criado com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if( txn.isActive() )
				txn.rollback();
		}
	}

	
	/**
	 * Method used by a BO or SU user to add an item to the shop
	 * @param data - itemName, providerName, description, pricePer, quantity, photo, at
	 * @return response message with status
	 */
	@POST
	@Path("/shopItem")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerShopItem(ShopItemData data) {
		LOG.info("Attempt to add item to shop.");
		
		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			//Check login
			Response r = checkLogin(txn, data.at, authToken);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "registerShopItem");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check data validity
			Response re = data.validDataRegister(txn);
			if( re.getStatus() != 200 ) {
				LOG.warning("Invalid data on shop item register. " + re.getEntity());
				txn.rollback();
				return re;
			}
			
			Key shopItemKey = shopItemKeyFactory.newKey(data.itemName.replaceAll("\\s+", "").toLowerCase());
			Entity shopItem = txn.get(shopItemKey);
			
			//Check if shop item with same name already exists
			if( shopItem != null ) {
				LOG.warning("Attempt to register shop item with existent name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Já existe um item com esse nome.").build();
			}
			
			//Create shop item
			shopItem = Entity.newBuilder(shopItemKey)
					.set("provider", data.providerName)
					.set("name", data.itemName)
					.set("description", data.description)
					.set("pricePer", data.pricePer)
					.set("quantity", data.quantity)
					.set("photo", DEFAULT_SHOP_IMG)
					.build();
			
			//If photo was added, upload photo and store link, otherwise use default image
			String fileName = "";
			
			//If photo was uploaded, add that too
			//Photo should never come as null, because web sends empty array, but just in case check
			if( data.photo == null ) {
				LOG.warning("Attempt to register item with null photo.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Foto não pode ser null.").build();
			}
			else if( data.photo.length != 0  ) {
				//Upload photo to cloud storage 
				fileName = SHOP_ITEMS + shopItemKey.getName() + System.currentTimeMillis();
				String fileLink = BUCKET_URL + fileName;
								
				r = uploadPhoto(txn, fileName, data.photo);
				if( r.getStatus() != 200 )
					return r;

				//Add photo link to shop item
				shopItem = Entity.newBuilder(shopItem)
						.set("photo", fileLink)
						.build();
			}
			
			//Add item to database
			txn.add(shopItem);
			
			//Add log of register to database
			r = addLog(txn, "registerShopItem", authToken.getString("username"), data.itemName);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			txn.commit();
			LOG.info("Shop item added successfully");
			return Response.ok().entity("Item adicionado à loja com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if( txn.isActive() )
				txn.rollback();
		}
	}
		

	/**
	 * Method used to register a new group in the app.
	 * Groups can be public (default) or private. If private, they require invites from the owner to join.
	 * Groups are limited to 1 per person, just as institutions
	 * @param data - name, capacity, description, at
	 * @return response message with status
	 *//*
	@POST
	@Path("/group")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response registerGroup(GroupData data) {
		LOG.info("Attempt to register group.");
		
		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			//Check login
			Response r = checkLogin(txn, data.at, authToken);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "registerGroup");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			Key userKey = userKeyFactory.newKey(authToken.getString("username").toLowerCase());
			Entity user = txn.get(userKey);
			
			//Check data validity
			Response re = data.validDataRegister();
			if( re.getStatus() != 200 ) {
				LOG.warning("Invalid given data on group register. " + re.getEntity());
				txn.rollback();
				return re;
			}
			
			String groupName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key groupKey = groupKeyFactory.newKey(groupName);
			Entity group = txn.get(groupKey);

			//Check if group with given name already exists in the database
			if( group != null ) {
				LOG.warning("Event with given name already exists on register, name: " + groupName);
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Já existe um grupo com o mesmo nome.").build();
			}
			
			//Create group with given data and an empty list of participants and events
			group = Entity.newBuilder(groupKey)
					.set("owner", authToken.getString("username"))
					.set("name", data.name)
					.set("capacity", data.capacity)
					.set("description", data.description)
					.set("eventsJoined", new ArrayList<Value<String>>())
					.set("participants", new ArrayList<Value<String>>())
					.set("private", false)
					.set("photo", "")
					.build();
			
			//If role is SU, stays SU. Otherwise, change role to GROUP_OWNER
			if( !user.getString("role").equals(SU) && !user.getString("role").equals(GROUP_OWNER) ) {
				user = Entity.newBuilder(user)
						.set("role", GROUP_OWNER)
						.build();
				
				//Get user's auth tokens
				String username = authToken.getString("username");
				Query<Entity> query = Query.newEntityQueryBuilder()
						.setKind("AuthToken")
						.setFilter(PropertyFilter.eq("username", username))
						.build();
				QueryResults<Entity> tasks = txn.run(query);
				
				//Change role on all of user's active tokens
				tasks.forEachRemaining(autTok -> { 	
					autTok = Entity.newBuilder(autTok)
							.set("role", GROUP_OWNER)
							.build();
					txn.put(autTok);
				});
			}
			
			//Add group to database
			txn.add(group);
			
			//Add log of register to database
			r = addLog(txn, "registerGroup", authToken.getString("username"), groupName);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			txn.commit();
			LOG.info("Group registered successfully with id " + groupName);
			return Response.ok().entity("Grupo criado com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if( txn.isActive() )
				txn.rollback();
		}
	}*/
	
	
	
	
	
	
	/**
	 * Method called on "register actions" to add a log to the database
	 * @param txn - active transaction from where the method was called
	 * @param methodName - methodName where the register action happened
	 * @param username - username of user who performed the action
	 * @param name - name of the new "thing"(event, inst, etc) that is being registered
	 * @return response message with status
	 */
	private Response addLog(Transaction txn, String methodName, String username, String name) {
		LOG.info("Adding register action to database log.");

		try {
			long currTime = System.currentTimeMillis();
			String method = methodName.replaceAll("\\s+", "").toLowerCase();
			Key logKey = logKeyFactory.newKey(currTime);
			Entity log = txn.get(logKey);
			
			log = Entity.newBuilder(logKey)
					.set("method", method.toLowerCase())
					.set("username", username)
					.set("name", name)
					.set("time", currTime)
					.build();
			
			txn.add(log);
			LOG.info("Register action added to databse log.");
			return Response.ok().build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe("Error on adding database log: " + e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} 
	}
	
	
	/**
	 * Method used to get the auth token from either the cache or the database
	 * @param tokenId - id of the user's token
	 * @return token entity
	 */
	private Entity getAuthToken(String tokenId) {		
		Entity authToken = null;
		if( cacheSupport.cache.containsKey(tokenId) ) 
			authToken = (Entity) cacheSupport.cache.get(tokenId);
		else if( tokenId != null && !tokenId.replaceAll("\\s+", "").equals("") ){
			Key authTokenKey = authTokenKeyFactory.newKey(tokenId);
			authToken = datastore.get(authTokenKey);
		}
		 return authToken;
	 }
	
	
	/**
	 * Used to check if a user is in a session, by verifying data of the authToken
	 * @param txn
	 * @param at
	 * @param authToken
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Response checkLogin(Transaction txn, AuthToken at, Entity authToken) {
		//Check both given token and database token
		if( at == null || authToken == null ) {
			LOG.warning("Attempt to operate with no login.");
			txn.rollback();
			return Response.status(Status.UNAUTHORIZED).entity("Login não encontrado.").build();
		}
		
		//If token is found, check for validity
		if( authToken.getLong("expirationDate") < System.currentTimeMillis() ) {
			LOG.warning("Auth Token expired.");
			txn.delete(authToken.getKey());
			txn.commit();
			return Response.status(Status.UNAUTHORIZED).entity("Auth Token expirado. Faça login antes de tentar novamente.").build();
		}
		
		//Update cache
		cacheSupport.cache.put(authToken.getKey().getName(), authToken);
		
		return Response.ok().build();
	}
	
	
	/**
	 * Method used to check the Role Based Access Control, to see if the user requesting can perform the action
	 * @param txn - active transaction from where the method was called
	 * @param role - role of the user performing the action
	 * @param methodName - name of the method that needs to be checked
	 * @return response message with status. If not 200, then user isn't allowed to perform action
	 */
	private Response checkRBAC(Transaction txn, String role, String methodName) {
		Key methodKey = rbacKeyFactory.newKey(methodName.toLowerCase());
		Entity method = txn.get(methodKey);

		//Check if method exists
		if( method == null ) {
			LOG.warning("Given method on RBAC check does not exist.");
			txn.rollback();
			return Response.status(Status.BAD_REQUEST).entity("Método especificado não existe.").build();
		}
		
		//Check RBAC
		if( method.getBoolean(role.toUpperCase()) == false ) {
			LOG.warning("User doesn't have permission for this action.");
			txn.rollback();
			return Response.status(Status.UNAUTHORIZED).entity("Você não tem permissões para realizar esta tarefa.").build();
		} 
		else
			return Response.ok().build();
	}
	
	
	/**
	 * Method used to upload a photo to the cloud storage
	 * @param txn - active transaction from where the method was invoked
	 * @param fileName - name of the file to store
	 * @param photo - bytes of the photo
	 * @return response message with status
	 * @throws IOException
	 */
	private Response uploadPhoto(Transaction txn, String fileName, byte[] photo) throws IOException {
		//Get file type
		InputStream is = new BufferedInputStream(new ByteArrayInputStream(photo));
		String mimeType = URLConnection.guessContentTypeFromStream(is);

		//Check if not null mime type, and if not, check if it's png or jpeg
		if( mimeType == null || (!mimeType.equals(PNG) && !mimeType.equals(JPEG)) ) {
			txn.rollback();
			return Response.status(Status.BAD_REQUEST).entity("Tipo de ficheiro não suportado. Tente png ou jpeg.").build();
		}
		
		Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();

		//Map<String, String> newMetadata = new HashMap<>();
		//newMetadata.put("Cache-Control", "max-age=0");
		//BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setMetadata(newMetadata).setContentType("image/png").build();
		BlobId blobId = BlobId.of(BUCKET_ID, fileName);
		BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/png").build();
							
		//storage.create(blobInfo, Files.readAllBytes(Paths.get(data.photo)));
		storage.create(blobInfo, photo);
		return Response.ok().build();
	}
	
	
	/**
	 * Method used to add a String to a list of Values String
	 * @param list - list where to add
	 * @param toAdd - string to add to list
	 * @return new list with added string
	 */
	private List<Value<String>> addStringToListValuesString(List<Value<String>> list, String toAdd) {
		List<Value<String>> listNew = new ArrayList<Value<String>>();

		for(Value<String> v : list) {
			listNew.add(v);
		}

		if( !listNew.contains(StringValue.of(toAdd)) )
			listNew.add(StringValue.of(toAdd));

		return listNew;
	}
	
	
		
}
