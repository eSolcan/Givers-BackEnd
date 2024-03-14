package giversapdc.resources;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

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

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.EntityQuery;
import com.google.cloud.datastore.Entity.Builder;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.PathElement;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StringValue;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.datastore.Transaction;
import com.google.cloud.datastore.Value;

import giversapdc.util.AccessControlData;
import giversapdc.util.AuthToken;
import giversapdc.util.CacheSupport;
import giversapdc.util.CodeValueData;
import giversapdc.util.CommentData;
import giversapdc.util.EventData;
//import giversapdc.util.GroupData;
import giversapdc.util.MapMarker;
import giversapdc.util.RegisterData;
import giversapdc.util.ShopItemData;
import giversapdc.util.SuggestionReportData;

@Path("/op")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class OperationsResource {
	
	//private static final String BO = "BO";
	private static final String SU = "SU";
	private static final String SUGGESTION = "Suggestion";
	private static final String REPORT = "Report";
	private static final String BUG = "Bug";
	private static final String SUPPORT_MAIL = "support@givers-volunteering.appspotmail.com";
	
	private static final String PNG = "image/png";
	private static final String JPEG = "image/jpeg";
	
	private static final String PROJECT_ID = "givers-volunteering";
	private static final String BUCKET_ID = "givers-volunteering.appspot.com";
	private static final String BUCKET_URL = "https://storage.googleapis.com/givers-volunteering.appspot.com/";
	private static final String EVENTS = "events/";
	
	private static final String SUGGEST_REPORT = "https://storage.googleapis.com/givers-volunteering.appspot.com/badges/suggestReport.png";
	private static final String EVENTS_5 = "https://storage.googleapis.com/givers-volunteering.appspot.com/badges/5events.png";
	private static final String EVENTS_10 = "https://storage.googleapis.com/givers-volunteering.appspot.com/badges/10events.png";
	private static final String EVENTS_25 = "https://storage.googleapis.com/givers-volunteering.appspot.com/badges/25events.png";
	
	private static final Logger LOG = Logger.getLogger(OperationsResource.class.getName());

	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
	private KeyFactory profileKeyFactory = datastore.newKeyFactory().setKind("Profile");
	private KeyFactory authTokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");
	private KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind("Event");
	//private KeyFactory groupKeyFactory = datastore.newKeyFactory().setKind("Group");
	private KeyFactory commentKeyFactory = datastore.newKeyFactory().setKind("Comment");
	private KeyFactory shopItemKeyFactory = datastore.newKeyFactory().setKind("Shop");
	private KeyFactory suggestionKeyFactory = datastore.newKeyFactory().setKind("Suggestion");
	private KeyFactory reportKeyFactory = datastore.newKeyFactory().setKind("Report");
	private KeyFactory bugKeyFactory = datastore.newKeyFactory().setKind("Bug");
	private KeyFactory registerVerifKeyFactory = datastore.newKeyFactory().setKind("RegisterVerification");
	private KeyFactory notificationKeyFactory = datastore.newKeyFactory().setKind("Notification");
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	private KeyFactory markerKeyFactory = datastore.newKeyFactory().setKind("Marker");
	private KeyFactory routeTrackingKeyFactory = datastore.newKeyFactory().setKind("RouteTracking");
	private KeyFactory photoEventLogKeyFactory = datastore.newKeyFactory().setKind("PhotoEventLog");
	private KeyFactory savedEventKeyFactory = datastore.newKeyFactory().setKind("SavedEvent");
	private KeyFactory shopPurchaseKeyFactory = datastore.newKeyFactory().setKind("ShopPurchase");
	private KeyFactory userEventsJoinedKeyFactory = datastore.newKeyFactory().setKind("UserEventsJoined");
	
	private KeyFactory rbacKeyFactory = datastore.newKeyFactory().setKind("AccessControl");
	
	private CacheSupport cacheSupport = new CacheSupport();
	
	public OperationsResource() { }

	
	/*
	 * Test method, used temporarily for testing email send with SMTP
	 *//*
	@POST
	@Path("/testSMTP/{mail}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response testSendMail(@PathParam("mail") String mail) {
		//Configure properties 
		Properties props = new Properties(); 

		//Account from where to send email 
		String from = "support@givers-volunteering.appspotmail.com"; 
		
		//Account where to send mail
		String to = mail;

		//Create session 
		Session session = Session.getInstance(props, null); 

		try{ 
			// Build message with the welcome image embedded 
			String msg = "<img src=\"https://storage.googleapis.com/givers-volunteering.appspot.com/welcomeemail.jpg\" "
					+ "style=\"display: block; margin-left: auto; margin-right: auto; width:25%;\">";

			Message message = new MimeMessage(session); 
			message.setFrom(new InternetAddress(from)); 
			message.setRecipient(Message.RecipientType.TO, new InternetAddress(to)); 
			message.setSubject("Welcome to Givers."); 
			message.setContent(msg, "text/html"); 
			
			
			//Send mail 
			Transport.send(message);
			return Response.ok().entity("Confirmação realizada com sucesso.").build();
			
		} catch(Exception e) {
			return Response.status(Status.BAD_REQUEST).entity("Erro no envio de mail de boas vindas." + e.getMessage()).build(); 
		}
	}
	*/
	
	
	  
	 
	 
	
	/**
	 * 	If confirmation is successful, a welcome mail will be sent
	 *  A link will be sent to the user's email that will redirect to a page which will call this rest end-point
	 *  If confirmation is successful, a welcome mail will be sent
	 * @param data - username, code
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/validateAccount")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response validateAccount(RegisterData data) {
		LOG.info("Attempt to validate account.");
		
		Transaction txn = datastore.newTransaction();
		try {
						
			//Check if given username is valid
			if( data.username == null || data.username.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to validate account with invalid username.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Username inválido.").build();
			}
			
			String username = data.username.replaceAll("\\s+", "").toLowerCase();
			Key registerVerifKey = registerVerifKeyFactory.newKey(username);
			Entity registerVerif = txn.get(registerVerifKey);

			//Check if verification exists
			if( registerVerif == null ) {
				LOG.warning("Attempt to verify account with null verification in database.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Verificação inválida.").build();
			}
			
			//Check if given code is valid
			if( data.code == null || data.code.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to validate account with invalid code.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Código inválido.").build();
			}
			
			//Check if code is correct
			if( !registerVerif.getString("code").equals(String.valueOf(data.code))) {
				LOG.warning("Attempt to validate account with incorrect verification code.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Código de confirmação incorreto.").build();
			}

			Key userKey = userKeyFactory.newKey(username);
			Entity user = txn.get(userKey);
			
			//Check if user exists
			if( user == null ) {
				LOG.warning("Attempt to validate account with unexistent user.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Utilizador não existe.").build();
			}
			
			//If all checks passed, change user account state to active and update database 
			user = Entity.newBuilder(user)
					.set("state", true)
					.build();
			
			txn.put(user);
			cacheSupport.cache.put(username, user);
			
			//Delete verification from database
			txn.delete(registerVerifKey);
			
			//Send welcome mail 
			//Configure properties 
			Properties props = new Properties(); 

			//Account from where to send email 
			String from = SUPPORT_MAIL; 
			
			//Account where to send mail
			String to = user.getString("email");

			//Create session 
			Session session = Session.getInstance(props, null); 

			try{ 
				// Build message with the welcome image embedded 
				String msg = "<img src=\"https://storage.googleapis.com/givers-volunteering.appspot.com/welcomeemail.jpg\" "
						+ "style=\"display: block; margin-left: auto; margin-right: auto; width:50%;\">";

				Message message = new MimeMessage(session); 
				message.setFrom(new InternetAddress(from)); 
				message.setRecipient(Message.RecipientType.TO, new InternetAddress(to)); 
				message.setSubject("Givers Volunteering - Bem Vindo."); 
				message.setContent(msg, "text/html"); 
				
				
				//Send mail 
				Transport.send(message);
			} catch (Exception e) { 
				txn.rollback(); 
				LOG.severe("Failed to send welcome email. \n" + e.getMessage());
				return Response.status(Status.BAD_REQUEST).entity("Erro no envio de mail de boas vindas.").build(); 
			} 
			
			txn.commit();
			return Response.ok().entity("Confirmação realizada com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used by a user to join an event.
	 * (Event can't be past or ongoing.) - This is false
	 * Had to change previous condition because of teachers. Still think that it makes more sense, but oh well.
	 * @param data - name, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/joinEventUser")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response joinEventUser(EventData data) {
		LOG.info("Attempt to join event.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "joinEventUser");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			//Check event name validity
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Invalid name on join event.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventName);
			Entity event = txn.get(eventKey);

			//Check if event exists
			if( event == null ) {
				LOG.warning("Event doesn't exist, on join event.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			/*
			//Check if event is ongoing or finished.
			if( event.getLong("date_start") < System.currentTimeMillis() ) {
				LOG.warning("Event is not available anymore on join event.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Evento já não está disponível.").build();
			}
			*/
			
			//Check if event is finished
			if( event.getLong("date_end") < System.currentTimeMillis() ) {
				LOG.warning("Event is not available anymore on join event.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Evento já decorreu.").build();
			}
			
			//Check if event is full.
			if( event.getLong("joinedUsersCount") == event.getLong("capacity") ) {
				LOG.warning("Event is full on join event.");
				txn.rollback();
				return Response.status(Status.NOT_ACCEPTABLE).entity("Evento está cheio.").build();
			}

			String username = authToken.getString("username");
			String eventId = event.getKey().getName();
			
			//Check if the user is already participating
			Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + username);
			Entity participation = txn.get(participationKey);
			
			if( participation != null ) {
				LOG.warning("User already joined the event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Você já está inscrito neste evento.").build();
			}

			//Add the user to the event's participations
			participation = Entity.newBuilder(participationKey)
					.set("eventId", eventId)
					.set("username", username)
					.set("participated", false)
					.build();
			
			//Edit the participants count of the event
			event = Entity.newBuilder(event)
					.set("joinedUsersCount", event.getLong("joinedUsersCount") + 1)
					.build();
			
			cacheSupport.cache.put(eventId, event);
			
			txn.put(participation, event);
			txn.commit();
			
			LOG.info("User joined event successfully: " + eventId + ", " + username);
			return Response.ok().entity("Inscrição no evento com sucesso.").build();
	
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}

	
	/**
	 * Method used by a user to leave an event in which he was going to participate
	 * Event can't be ongoing or past
	 * @param data - name, at
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/leaveEventUser")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response leaveEventUser(EventData data) {
		LOG.info("Attempt to leave event.");

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
			r = checkRBAC(txn, authToken.getString("role"), "leaveEventUser");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Invalid event name on leave event user.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventId);
			Entity event = txn.get(eventKey);

			//Check if event exists in database
			if( event == null ) {
				LOG.warning("Event doesn't exist, on leave event.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			String username = authToken.getString("username");
			
			//Check if the user is joined. If so, check if he's already participated
			Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + username);
			Entity participation = txn.get(participationKey);
			
			if( participation == null ) {
				LOG.warning("User isn't participating in leave event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Já não pertence a este evento.").build();
			}
			else if( participation.getBoolean("participated") ) {
				LOG.warning("Attempt to leave event in which he already participated on android.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não pode sair deste evento, visto que já fez a rota.").build();
			}
			
			Key userKey = userKeyFactory.newKey(username);
			Entity user = txn.get(userKey);
			
			//Check if user is currently doing this event
			if( user.getString("inEvent").equalsIgnoreCase(eventId) ) {
				LOG.warning("Attempt to leave event in which user is currently partaking android tracking.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não pode sair deste evento, visto que está a realizar a rota neste momento.").build();
			}

			//Check if event has finished.
			if( event.getLong("date_end") < System.currentTimeMillis() ) {
				LOG.warning("Event is not available anymore, on leave event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento já terminou, não pode cancelar a inscrição.").build();
			}
			
			//Edit the participants count of the event
			event = Entity.newBuilder(event)
					.set("joinedUsersCount", event.getLong("joinedUsersCount") - 1)
					.build();
			
			//Update event in cache and database
			cacheSupport.cache.put(eventId, event);
			txn.put(event);
			
			//Remove participation from database
			txn.delete(participationKey);
			txn.commit();
			
			LOG.info("User " + username + " left event successfully: " + eventId);
			return Response.ok().entity("Cancelou a inscrição do evento com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}

	
	/**
	 * Method used by a participant of an event to comment on it.
	 * @param data - eventName, commentText, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/commentEvent")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response commentEvent(CommentData data) {
		LOG.info("Attempt to comment on event.");

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
			r = checkRBAC(txn, authToken.getString("role"), "commentEvent");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			//Check input data
			r = data.validData(txn);
			if( r.getStatus() != 200 ) {
				LOG.warning("Attempt to comment event with invalid data. " + r.getEntity());
				txn.rollback();
				return r;
			}
			
			String eventId = data.eventName.replaceAll("\\s+", "").toLowerCase();
			String username = authToken.getString("username");
			Key eventKey = eventKeyFactory.newKey(eventId);
			Entity event = txn.get(eventKey);

			//Check if event exists
			if( event == null ) {
				LOG.warning("Event doesn't exist on comment event.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			//Check if user is participating in the event
			Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + username);
			Entity participation = txn.get(participationKey);
			
			if( participation == null ) {
				LOG.warning("Attempt to comment on event in which user is not particpating.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Deve-se inscrever neste evento para poder comentar nele.").build();
			}
			
			/*
			//Check if event is ongoing
			if( event.getLong("date_start") > System.currentTimeMillis() || event.getLong("date_end") < System.currentTimeMillis() ) {
				LOG.warning("Attempt to comment in not ongoing event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento ainda não decorrente, não pode comentar.").build();
			}*/	
			
			//Check if user commented in the last x seconds
			Key commentCooldownKey = codeValueKeyFactory.newKey("commentcooldown");
			long commentCooldown = datastore.get(commentCooldownKey).getLong("value");
			
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("Comment")
					.setFilter(PropertyFilter.hasAncestor(eventKeyFactory.newKey(eventId)))
					.setFilter(CompositeFilter.and(PropertyFilter.eq("owner", authToken.getString("username")), 
													PropertyFilter.ge("date", System.currentTimeMillis() - commentCooldown )))
					.build(); 
			
			QueryResults<Entity> tasks = datastore.run(query);
			
			if( tasks.hasNext() ) {
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("É permitido apenas um comentário a cada " + commentCooldown/1000 + "segundos.").build();
			}
			
			//Get profile to get photo link. From cache if present, otherwise from database
			Entity profile = (Entity) cacheSupport.cache.get(username + "profile");

			//Get profile from database, if wasn't present in cache
			if( profile == null ) {
				Key profileKey = profileKeyFactory
						.addAncestor(PathElement.of("User", username))
						.newKey(username);
				profile = txn.get(profileKey);
			}	
			
			if( profile == null ) {
				LOG.warning("Attempt to comment with null profile. " + username);
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Perfil null.").build();
			}
			else
				cacheSupport.cache.put(authToken.getString("username") + "profile", profile);
				
			String profilePhoto = profile.getString("photo");
			
			//Create comment
			String commentId = eventId + username + System.currentTimeMillis();
			Key commentKey = commentKeyFactory.addAncestor(PathElement.of("Event", eventId)).newKey(commentId);
			
			Builder comment = Entity.newBuilder(commentKey)
					.set("comment", data.commentText)
					.set("owner", username)
					.set("date", System.currentTimeMillis())
					.set("photoLink", "")
					.set("photoId", "")
					.set("profilePhoto", profilePhoto)
					.set("commentId", commentId);

			//If a photo was added as well, upload and edit photo property
			//Photo should never come as null, because web sends empty array, but just in case, check
			if( data.photo == null ) {
				LOG.warning("Attempt to register event with null photo.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Foto não pode ser null.").build();
			}
			else if( data.photo.length != 0 ) {

				//Create file name and link
				String fileName = EVENTS + eventId + "/" + System.currentTimeMillis();
				String fileLink = BUCKET_URL + fileName;
				
				//Get file type
				InputStream is = new BufferedInputStream(new ByteArrayInputStream(data.photo));
				String mimeType = URLConnection.guessContentTypeFromStream(is);

				//Check if not null mime type, and if not, check if it's png or jpeg
				if( mimeType == null || (!mimeType.equals(PNG) && !mimeType.equals(JPEG)) ) {
					txn.rollback();
					return Response.status(Status.BAD_REQUEST).entity("Tipo de ficheiro não suportado. Tente png ou jpeg.").build();
				}
				
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();

				BlobId blobId = BlobId.of(BUCKET_ID, fileName);
				BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/png").build();
									
				//Add photo to cloud storage
				storage.create(blobInfo, data.photo);
				
				//Add photo link and id to the comment
				comment.set("photoLink", fileLink)
						.set("photoId", fileName);
				
				//Add photo "log" to database
				Key photoEventLogKey = photoEventLogKeyFactory.newKey(System.currentTimeMillis());
				Entity photoEventLog = Entity.newBuilder(photoEventLogKey)
						.set("markerId", "")
						.set("eventId", eventId)
						.set("photoLink", fileLink)
						.set("owner", authToken.getString("username"))
						.build();
				
				txn.put(photoEventLog);
			}
			
			txn.put(comment.build());
			txn.put(event);

			cacheSupport.cache.put(eventKey.getName(), event);
				
			txn.commit();
			
			LOG.info("User commented on event successfully.");
			return Response.ok().entity("Comentou com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used to send a suggestion/report/bug ticket to the admins.
	 * @param data - type, subtype, text, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/suggestionReport")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response addSuggestionReport(SuggestionReportData data) {
		LOG.info("Attempt to add suggestion or report.");

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
			r = checkRBAC(txn, authToken.getString("role"), "addSuggestionReport");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Validate given data
			r = data.validData(txn);
			if( r.getStatus() != 200 ) {
				LOG.warning("Invalid data on suggestion/report. " + r.getEntity());
				txn.rollback();
				return r;
			}
			
			//For spam protection, check if last suggestion/report by the user was more than 60s ago
			//Prepare query
			EntityQuery.Builder query = Query.newEntityQueryBuilder()
					.setFilter(CompositeFilter.and(PropertyFilter.eq("username", authToken.getString("username")), 
							PropertyFilter.ge("creation_time", System.currentTimeMillis() - 60*1000 )));
			
			//Verify kind, and set it depending on what user specified
			if( data.type.equalsIgnoreCase(SUGGESTION) )
				query.setKind(SUGGESTION);
			else if( data.type.equalsIgnoreCase(REPORT) )
				query.setKind(REPORT);
			else if( data.type.equalsIgnoreCase(BUG) )
				query.setKind(BUG);
			else {
				LOG.warning("Attempt to add suggestion/report/bug with invalid type.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Tipo que introduziu não e suportado.").build();
			}
			
			QueryResults<Entity> tasks = datastore.run(query.build());
			
			if( tasks.hasNext() ) {
				LOG.warning("Attempt to add suggestion/report/bug more than once a minute.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("É permitido enviar apenas uma sugestão/denúncia/bug por minuto.").build();
			}
								
			//Create random ID for the message to be uploaded
			String id = UUID.randomUUID().toString();
			Key toAddKey = null;
			
			//Create key with given type
			if( data.type.equalsIgnoreCase(SUGGESTION) )
				toAddKey = suggestionKeyFactory.newKey(id);
			else if( data.type.equalsIgnoreCase(REPORT) )
				toAddKey = reportKeyFactory.newKey(id);
			else if( data.type.equalsIgnoreCase(BUG) )
				toAddKey = bugKeyFactory.newKey(id);
				
			//Create and add the message to the database
			Entity toAdd = Entity.newBuilder(toAddKey)
					.set("username", authToken.getString("username"))
					.set("type", data.type.toLowerCase())
					.set("subtype", data.subtype)
					.set("text", data.text)
					.set("checked", false)
					.set("creation_time", System.currentTimeMillis())
					.set("name", id)
					.build();
			
			//If user didn't have the badge before, add SUGGEST_REPORT badge
			String username = authToken.getString("username");
			Key profileKey = profileKeyFactory.addAncestors(PathElement.of("User", username)).newKey(username);
			Entity profile = txn.get(profileKey);
			List<Value<String>> badges = profile.getList("badges");
					
			if( !badges.contains(StringValue.of(SUGGEST_REPORT)) ) {
				profile = Entity.newBuilder(profile)
						.set("badges", addStringToListValuesString(profile.getList("badges"), SUGGEST_REPORT))
						.build();
			}
			
			txn.add(toAdd);
			txn.put(profile);
			
			cacheSupport.cache.put(authToken.getString("username") + "profile", profile);
			
			txn.commit();
			
			LOG.info("Suggestion/Report/Bug added successfully.");
			return Response.ok().entity("Mensagem adicionada com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used to add an event to the user's list of saved/favourite events.
	 * @param data - name, at
	 * @return response message with status
	 */
	@POST
	@Path("/saveEvent")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response saveEvent(EventData data) {
		LOG.info("Attempt to save an event.");

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
			r = checkRBAC(txn, authToken.getString("role"), "saveEvent");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check given event name
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to save event with invalid event name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			//Check if event exists in the database
			String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventId);
			Entity event = txn.get(eventKey); 
			
			if( event == null ) {
				LOG.warning("Attempt to save unexistent event.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			String eventName = event.getString("name");
			String username = authToken.getString("username");

			//Check if user already saved the event
			Key savedEventKey = savedEventKeyFactory.newKey(eventId + username);
			Entity savedEvent = txn.get(savedEventKey);
			
			if( savedEvent != null ) {
				LOG.warning("Attempt to save already saved event.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Evento já está guardado na sua lista de favoritos.").build();
			}
			
			//Add event to user's saved events
			savedEvent = Entity.newBuilder(savedEventKey)
					.set("eventName", eventName)
					.set("username", username)
					.build();

			txn.put(savedEvent);
			txn.commit();
			
			LOG.info("Event saved successfully.");
			return Response.ok().entity("Evento guardado com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used by a user to start an event. 
	 * Must input code given by the owner/manager of the event.
	 * On the android app, GPS tracking should start.
	 * @param data - name, joinCode, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/userStartEvent")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response userStartEvent(EventData data) {
		LOG.info("Attempt to start an event by a user.");

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
			r = checkRBAC(txn, authToken.getString("role"), "userStartEvent");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check validity of event name
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to start event with invalid event name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome inválido.").build();
			}
			
			//Check if event exists in the database
			String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventId);
			Entity event = txn.get(eventKey);
			if( event == null ) {
				LOG.warning("Attempt to start unexistent event.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			String username = authToken.getString("username");
			Key userKey = userKeyFactory.newKey(username);
			Entity user = txn.get(userKey);
			
			//Check if the user is participating
			Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + username);
			Entity participation = txn.get(participationKey);
			
			//Check if user is registered in event
			if( participation == null ) {
				LOG.warning("Attempt to start event in which user is not participating.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Não está a inscrito para participar neste event.").build();
			}
	
			//If the user is already in another event, can't join 
			if( !user.getString("inEvent").equals("") ) {
				LOG.warning("Attempt to start an event while already in another event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Já está a participar num evento decorrente, não pode iniciar outro.").build();
			}
			
			//Check if user already participated in said event
			if( participation.getBoolean("participated") ) {
				LOG.warning("Attempt to start already participated event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Já participou neste evento.").build();
			}
			
			//Check if event is past/future
			if( event.getLong("date_start") > System.currentTimeMillis() || event.getLong("date_end") < System.currentTimeMillis() ) {
				LOG.warning("Attempt to start past/future event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento não disponível (passado ou futuro).").build();
			}

			//Check if given code is correct
			if( data.joinCode == null || !data.joinCode.equals(event.getString("start_code")) ) {
				LOG.warning("Invalid start code.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Código de validação incorreto.").build();
			}
			
			//Change user to be "inEvent", with string of this eventId. Participation will become true at the endEvent
			user = Entity.newBuilder(user)
					.set("inEvent", eventId)
					.build();
			
			txn.put(user);
			cacheSupport.cache.put(username, user);
			
			txn.commit();
			
			LOG.info("User started event successfully.");
			return Response.ok().entity("Evento iniciado com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used to end an event.
	 * On the android app, GPS tracking must be stopped and tracking data sent to this method for storage on the database
	 * @param data - name, rating, markers, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/userEndEvent")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response userEndEvent(EventData data) {
		LOG.info("Attempt to start an event by a user.");

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
			r = checkRBAC(txn, authToken.getString("role"), "userEndEvent");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check event name validity
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ){
				LOG.warning("Attempt to finish event with invalid name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
			String username = authToken.getString("username");
			Key userKey = userKeyFactory.newKey(username);
			Entity user = txn.get(userKey);
			
			Key eventKey = eventKeyFactory.newKey(eventId);
			Entity event = txn.get(eventKey);
			
			//Check if event exists in database
			if( event == null ) {
				LOG.warning("Attempt to end event on unexistent event.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Evento não existe.").build();
			}
			
			//Check if user is participating in this event
			Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + username);
			Entity participation = txn.get(participationKey);
		
			if( participation == null ) {
				LOG.warning("Attempt to end event in which was not participated.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não está a participar neste evento.").build();
			}
			
			//Check if user is routing in this event
			if( !user.getString("inEvent").equals(eventId) ) {
				LOG.warning("Attempt to finish event while not inEvent this.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não está a realizar a rota deste evento.").build();
			}
 	
			Key codeValueKey = codeValueKeyFactory.newKey("eventpointsreward");
			Entity codeValue = txn.get(codeValueKey);
			long points = codeValue.getLong("value");

			//Check if at least one marker has been sent
			if( data.markers == null || data.markers.length == 0 ) {
				LOG.warning("Attempt to finish event with 0 markers.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Deve terminar com pelo menos uma posição GPS.").build();
			}
			
			//Give points depending on time spent compared to duration of event
			//The attribution of points is by far not ideal, and is extremely abusable, but will have to do for now			
			long timeSpent = data.markers[data.markers.length - 1].time - data.markers[0].time;
			if( timeSpent == 0 )
				timeSpent = 1;
			long eventDuration = event.getLong("date_end") - event.getLong("date_start");
			
			//Give points depending on time spent in relation to duration of event
			double timeRatio = eventDuration/timeSpent;
			double result = points/timeRatio;
			points = Double.valueOf(result).longValue();
			if( points == 0 )
				points = 1;

			user = Entity.newBuilder(user)
					.set("inEvent", "")
					.set("shopPoints", user.getLong("shopPoints") + points)
					.set("scorePoints", user.getLong("scorePoints") + points)
					.build();
			
			//Set user to participated in this event
			participation = Entity.newBuilder(participation)
					.set("participated", true)
					.build();
					
			//Add path markers to database
			Key markerKey;
			int i = 0;
			for( MapMarker m : data.markers ) {
				if( i < 10 )
					markerKey = routeTrackingKeyFactory.newKey(eventId + username + "000" + i);
				else if( i >= 10 && i < 100 )
					markerKey = routeTrackingKeyFactory.newKey(eventId + username + "00" + i);
				else if( i >= 100 && i < 1000 )
					markerKey = routeTrackingKeyFactory.newKey(eventId + username + "0" + i);
				else
					markerKey = routeTrackingKeyFactory.newKey(eventId + username + "" + i);
					
				Entity marker = Entity.newBuilder(markerKey)
						.set("lat", m.lat)
						.set("lng", m.lng)
						.set("time", m.time)
						.set("username", username)
						.set("event", eventId)
						.build();
				txn.put(marker);
				i++;
			}
			
			//Add rating to event
			//Check if rating is valid first
			if( data.rating < 1 || data.rating > 5 ) {
				LOG.warning("Invalid rating of event on event end.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Rating fornecido é inválido, mínimo 1 e máximo 5.").build();
			}
			
			//Sum the rating to the total, and increment nrOfRatings by one
			double newTotalRating = event.getDouble("totalRating") + data.rating; 
			long newNrRatings = event.getLong("nrOfRatings") + 1;
			double newActualRating = newTotalRating/newNrRatings;

			event = Entity.newBuilder(event)
					.set("totalRating", newTotalRating)
					.set("nrOfRatings", newNrRatings)
					.set("actualRating", newActualRating )
					.build();
			
			//Check the user for badges, update as/if needed
			//Get all of user's completed events
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("UserEventsJoined")
					.setFilter(CompositeFilter.and(
							PropertyFilter.eq("username", username), 
							PropertyFilter.eq("participated", true)))
					.build();
			QueryResults<Entity> tasks = txn.run(query);
			
			int nrEvents = 0;
			while( tasks.hasNext() ) {
				tasks.next();
				nrEvents++;
			}
			
			Key profileKey = profileKeyFactory.addAncestors(PathElement.of("User", username)).newKey(username);
			Entity profile = txn.get(profileKey);
			List<Value<String>> badges = profile.getList("badges");
			
			//Check badge based on number of participated events
			if( nrEvents >= 25 ) {
				if( !badges.contains(StringValue.of(EVENTS_25)) ) {
					profile = Entity.newBuilder(profile)
							.set("badges", addStringToListValuesString(profile.getList("badges"), EVENTS_25))
							.build();
				}
			}
			else if( nrEvents >= 10 ) {
				if( !badges.contains(StringValue.of(EVENTS_10)) ) {
					profile = Entity.newBuilder(profile)
							.set("badges", addStringToListValuesString(profile.getList("badges"), EVENTS_10))
							.build();
				}
			}
			else if( nrEvents >= 5 ) {
				if( !badges.contains(StringValue.of(EVENTS_5)) ) {
					profile = Entity.newBuilder(profile)
							.set("badges", addStringToListValuesString(profile.getList("badges"), EVENTS_5))
							.build();
				}
			}

			txn.put(user, profile, event, participation);
			cacheSupport.cache.put(username, user);
			cacheSupport.cache.put(username + "profile", profile);
			cacheSupport.cache.put(eventId, event);
			
			txn.commit();
			
			LOG.info("Event finalized successfully.");
			return Response.ok().entity("Evento finalizado com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used to purchase an item from the shop.
	 * User must have enough coins to purchase.
	 * An email will be sent with the voucher code.
	 * @param data - itemName, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/purchaseShopItem")
	public Response purchaseShopItem(ShopItemData data) {
		LOG.info("Attempt to purchase shop item.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "purchaseShopItem");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			//Check validity of item name
			if( data.itemName == null || data.itemName.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to purchase shop item with invalid item name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do item inválido.").build();
			}
			
			String shopItemId = data.itemName.replaceAll("\\s+", "").toLowerCase();
			Key shopItemKey = shopItemKeyFactory.newKey(shopItemId);
			Entity shopItem = txn.get(shopItemKey);
			
			String username = authToken.getString("username");
			Key userKey = userKeyFactory.newKey(username);
			Entity user = txn.get(userKey);
			
			//Check if item exits in database
			if( shopItem == null ) {
				LOG.warning("Attempt to purchase unexistent shop item.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Não existe um item com esse nome.").build();
			}
			
			//Check if user already purchased said item
			Key shopPurchaseKey = shopPurchaseKeyFactory.newKey(shopItemId + username);
			Entity shopPurchase = txn.get(shopPurchaseKey);
			
			if( shopPurchase != null ) {
				LOG.warning("Attempt to purchase already purchased item.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Itens limitados a um por pessoa (já comprou este).").build();
			}
			
			//Check if user has enough shop points to purchase item
			if( user.getLong("shopPoints") < shopItem.getLong("pricePer") ) {
				LOG.warning("Attempt to purchase shop item with unsufficient funds.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Não tem pontos suficientes para comprar este item.").build();
			}
			
			//Create entry for the user's shop purchase and lower points accordingly
			user = Entity.newBuilder(user)
					.set("shopPurchases", addStringToListValuesString(user.getList("shopPurchases"), shopItemId))
					.set("shopPoints", user.getLong("shopPoints") - shopItem.getLong("pricePer"))
					.build();
			
			//Lower the stock
			shopItem = Entity.newBuilder(shopItem)
					.set("quantity", shopItem.getLong("quantity") - 1)
					.build();
			
			//Create entity
			shopPurchase = Entity.newBuilder(shopPurchaseKey)
					.set("shopItemId", shopItemId)
					.set("username", username)
					.build();
			
			//Create random 6 digit voucher code
			Random rnd = new Random();
			int nrRnd = 100000 + rnd.nextInt(900000);
			
			//Send email with voucher code
			//Configure properties 
			Properties props = new Properties(); 

			//Account from where to send email 
			String from = SUPPORT_MAIL; 
			
			//Account where to send mail
			String to = user.getString("email");

			//Create session 
			Session session = Session.getInstance(props, null); 

			try{ 
				// Build message with the welcome image embedded 
				String msg = "<p style=\"font-family: Arial\">Olá username!</p>\r\n"
						+ "<p style=\"font-family: Arial\">Comprou um item na loja do Givers Volunteering: " + data.itemName 
						+ " do fornecedor " + shopItem.getString("provider") + ".</p>\r\n"
						+ "<br>\r\n"
						+ "<p style=\"font-family: Arial\">O código do seu voucher é <b>" + nrRnd + "</b>.</p>\r\n"
						+ "<br>\r\n"
						+ "<p style=\"font-family: Arial\">Obrigado,</p>\r\n"
						+ "<p style=\"font-family: Arial\">Equipa NovaWare</p>";

				Message message = new MimeMessage(session); 
				message.setFrom(new InternetAddress(from)); 
				message.setRecipient(Message.RecipientType.TO, new InternetAddress(to)); 
				message.setSubject("Givers Volunteering - Voucher"); 
				message.setContent(msg, "text/html"); 
				
				
				//Send mail 
				Transport.send(message);
			} catch (Exception e) { 
				txn.rollback(); 
				LOG.severe("Failed to send welcome email. \n" + e.getMessage());
				return Response.status(Status.BAD_REQUEST).entity("Erro no envio de mail de boas vindas.").build(); 
			} 
			
			txn.put(shopItem, user, shopPurchase);
			cacheSupport.cache.put(username, user);
			
			txn.commit();
			
			LOG.info("Shop item purchased successfully.");
			return Response.ok().entity("Item adquirido com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
		
	
	/**
	 * Method used by a BO or SU user to notify a user that his suggestion/report/bug have been checked and resolved
	 * @param data - type, name, at
	 * @return response message with status
	 */
	@POST
	@Path("/checkSuggestionReport")
	public Response checkSuggestionReport(SuggestionReportData data) {
		LOG.info("Attempt to mark notification or suggestion as checked.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "checkSuggestionReport");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			//Check name validity
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to check suggestion with invalid name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome invaálido.").build();
			}
			
			//Check type validity
			if( data.type == null || data.type.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to check suggestion with invalid type.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Tipo inválido.").build();
			}
			
			Key srbKey = null;
			Entity srb = null;
			
			if( data.type.equalsIgnoreCase(SUGGESTION) ) 
				srbKey = suggestionKeyFactory.newKey(data.name);
			else if( data.type.equalsIgnoreCase(REPORT) ) 
				srbKey = reportKeyFactory.newKey(data.name);
			else if( data.type.equalsIgnoreCase(BUG) ) 
				srbKey = bugKeyFactory.newKey(data.name);
			else {
				LOG.warning("Attempt to mark suggestion/report/bug as checked on unexistent type.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Tipo que introduziu não existe.").build();
			}

			//Get the suggestion/report
			srb = txn.get(srbKey);
			
			System.out.println(srb);
			//Check if suggestion/report exists
			if( srb == null ) {
				LOG.warning("Specified suggestion/report does not exist.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Sugestão/denúncia/bug indicada não existe.").build();
			}
			
			//Mark suggestion/report/bug as checked
			srb = Entity.newBuilder(srb)
					.set("checked", true)
					.build();
			
			//Create notification for user
			Key notificationKey = notificationKeyFactory.newKey(srb.getString("username") + srb.getKey().getName());
			Entity notification = Entity.newBuilder(notificationKey)
					.set("username", srb.getString("username"))
					.set("text", "Sugestão/Denúncia/Bug que nos enviou com o título " + srb.getString("subtype") 
							+ " foi tratada por um admin. Obrigado.")
					.set("delivered", false)
					.build();
			txn.put(notification);
			
			//Update the srb in the database
			txn.put(srb);
			txn.commit();
			
			LOG.info("Suggestion/Report/Bug checked successfully.");
			return Response.ok().entity("Sugestão/Denúncia/Bug verificada com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
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
	 * Method used by a participant to add a Photo to a specific marker of the event
	 * @param data - name, markerId, photo, at
	 * @return response message with status
	 */
	@POST
	@Path("/addPhotoMarker")
	public Response addPhotoMarker(EventData data) {
		LOG.info("Attempt to add photo to event marker.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "addPhotoMarker");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check event name validity
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to add photo to marker with invalid event name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome inválido.").build();
			}
			
			String username = authToken.getString("username");
			String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventId);
			Entity event = txn.get(eventKey);
			
			Key userKey = userKeyFactory.newKey(username);
			Entity user = txn.get(userKey);

			//Check if event exists in database
			if( event == null ) {
				txn.rollback();
				LOG.warning("Attetmp to add photo to unexistent event.");
				return Response.status(Status.BAD_REQUEST).entity("Evento não existe.").build();
			}

			//Check if user is participating in event and is currently "inEvent" for this event
			Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + username);
			Entity participation = txn.get(participationKey);
			
			if( participation == null ) {
				LOG.warning("Attempt to upload photo to event by not participant.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não participa neste event, não pode fazer upload de imagens.").build();
			} 
			else if( !user.getString("inEvent").equals(eventId) ) {
				LOG.warning("Attempt to upload photo to event by not in route participant.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Para adicionar foto, deve começar a rota na app Android.").build();
			}
			
			//Check if event is ongoing
			if( event.getLong("date_start") > System.currentTimeMillis() || event.getLong("date_end") < System.currentTimeMillis() ) {
				LOG.warning("Attempt to add photo to past/future event.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Evento não decorrente, não pode adicionar foto.").build();
			}
			
			//Check validity of markerId
			if( data.markerId == null || data.markerId.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to add photo to marker with invalid markerId.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Marker id inválido.").build();
			}
			
			KeyFactory mkKeyFactory = markerKeyFactory.addAncestor(PathElement.of("Event", eventId));
			Key markerKey = mkKeyFactory.newKey(data.markerId);
			Entity marker = txn.get(markerKey);
			
			//Check if given marker exists
			if( marker == null ) {
				txn.rollback();
				LOG.warning("Attempt to add photo to unexistent map marker.");
				return Response.status(Status.BAD_REQUEST).entity("Marcador não existe.").build();
			}
						
			//Upload photo to cloud storage 
			String fileName = EVENTS + eventId + "/" + username + System.currentTimeMillis();
			String fileLink = BUCKET_URL + fileName;
			try {
				r = uploadPhoto(txn, fileName, data.photo);
				if( r.getStatus() != 200 )
					return r;
			} catch(Exception e) {
				txn.rollback();
				LOG.severe(e.getMessage());
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro na leitura do ficheiro.").build();
			}
			
			//Add photo "log" to database
			Key photoEventLogKey = photoEventLogKeyFactory.newKey(System.currentTimeMillis());
			Entity photoEventLog = Entity.newBuilder(photoEventLogKey)
					.set("markerId", marker.getKey().getName())
					.set("eventId", eventId)
					.set("photoLink", fileLink)
					.set("owner", authToken.getString("username"))
					.build();
			
			txn.put(photoEventLog);
			
			txn.commit();
			
			LOG.info("Picture added to marker successfully.");
			return Response.ok().entity("Foto adicionada com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	
	
	
	
	
	
	/**
	 * Method used by a BO or SU user to add a new code value to the database
	 * @param data - name, newValue, at
	 * @return response message with status
	 */
	@POST
	@Path("/addCodeValue")
	public Response addCodeValue(CodeValueData data) {
		LOG.info("Attempt to add a new code value.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "addCodeValue");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check code value name validity
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to add code value with invalid name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do código inválido.").build();
			}
			
			Key codeValueKey = codeValueKeyFactory.newKey(data.name.replaceAll("\\s+", "").toLowerCase());
			Entity codeValue = txn.get(codeValueKey);
			
			//Check given value exists in database
			if( codeValue != null ) {
				LOG.warning("Given code value already exist.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Código já existe.").build();
			}
			
			//Check data validity
			if( data.newValue <= 0  ) {
				LOG.warning("Invalid data on edit code value.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Dados inválidos.").build();
			}
			
			//Add new code value		
			codeValue = Entity.newBuilder(codeValueKey)
					.set("value", data.newValue)
					.build();
			
			txn.add(codeValue);
			txn.commit();
			LOG.info("Code value added successfully.");
			return Response.ok().entity("Valor adicionado com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Used by a BO or SU user to edit certain code constants, or verifications
	 * @param data - name, newVal
	 * @return response message with status
	 */
	@POST
	@Path("/editCodeValue")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editCodeValue(CodeValueData data) {
		LOG.info("Attempt to edit shop item.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editCodeValue");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			//Check code value name validity
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to add code value with invalid name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do código inválido.").build();
			}
			
			Key codeValueKey = codeValueKeyFactory.newKey(data.name.replaceAll("\\s+", "").toLowerCase());
			Entity codeValue = txn.get(codeValueKey);
			
			//Check if code value exits in database
			if( codeValue == null ) {
				LOG.warning("Attempt to edit code value.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Não existe um valor com esse nome.").build();
			}
			
			//Check data validity
			if( data.newValue < 0  ) {
				LOG.warning("Invalid data on edit code value.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Dados inválidos.").build();
			}
			
			//Edit shop item
			codeValue = Entity.newBuilder(codeValue)
					.set("value", data.newValue)
					.build();
			
			txn.put(codeValue);
			txn.commit();
			
			LOG.info("Edited value successfully on: " + codeValue.getKey().getName());
			return Response.ok().entity("Valor editado com sucesso.").build();
			
		} catch (Exception e) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used by a BO or SU user to add a new method to the Role Based Access Control on the database
	 * @param data - methodName, user, instOwner, groupOwner, bo, at
	 * @return response message with status
	 */
	@POST
	@Path("/addAccessControl")
	public Response addAccessControl(AccessControlData data) {
		LOG.info("Attempt to add access control method.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "addAccessControl");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check method name validity
			if( data.methodName == null ||  data.methodName.replaceAll("\\s+", "").length() <= 3 ) {
				LOG.warning("Attempt to register RBAC with less than 3 characters.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do método deverá conter pelo menos 3 caracteres.").build();
			}
			
			String methodName = data.methodName.replaceAll("\\s+", "").toLowerCase();
			Key methodKey = rbacKeyFactory.newKey(methodName);
			Entity method = txn.get(methodKey);
			
			//Check if method exists
			if( method != null ) {
				LOG.warning("Method already exists.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Método já existe.").build();
			}
			
			//Add new method to RBAC, default usable only to SU and BO			
			method = Entity.newBuilder(methodKey)
					.set("USER", data.user)
					.set("BO", data.bo)
					.set("INST_OWNER", data.instOwner)
					.set("GROUP_OWNER", data.groupOwner)
					.set("SU", true)
					.build();
			
			txn.add(method);
			txn.commit();
			LOG.info("Method added successfully.");
			return Response.ok().entity("Método adicionado com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used by a BO or SU user to edit a method on the RBAC table in the database
	 * @param data - methodName, user, instOwner, groupOwner, bo, at
	 * @return response message with status
	 */
	@POST
	@Path("/editAccessControl")
	public Response editAccessControl(AccessControlData data) {
		LOG.info("Attempt to change access control rule.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "editAccessControl");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			//Check method name validity
			if( data.methodName == null ||  data.methodName.replaceAll("\\s+", "").length() <= 3 ) {
				LOG.warning("Attempt to register RBAC with less than 3 characters.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do método deverá conter pelo menos 3 caracteres.").build();
			}
						
			String methodName = data.methodName.replaceAll("\\s+", "").toLowerCase();
			
			if( methodName.length() <= 3 ) {
				LOG.warning("Attempt to edit RBAC with less than 3 characters.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do método deverá conter pelo menos 3 caracteres.").build();
			}
			
			Key methodKey = rbacKeyFactory.newKey(methodName);
			Entity method = txn.get(methodKey);
			
			//Check if method exists
			if( method == null ) {
				LOG.warning("Method doesn't exist.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Método especificado não existe.").build();
			}

			//Edit role permissions
			Builder methodBuilder = Entity.newBuilder(method)
					.set("USER", data.user)
					.set("INST_OWNER", data.instOwner)
					.set("GROUP_OWNER", data.groupOwner);
			
			//If editing role is SU, can also change BO permission
			if( authToken.getString("role").equals(SU) )
				methodBuilder.set("BO", data.bo);
			
			//Build the new updated method
			method = methodBuilder.build();
			
			txn.put(method);
			txn.commit();
			
			LOG.info("Role changed successfully.");
			return Response.ok().entity("Permissões alteradas com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}
	
	
	
	/**
	 * Method used by a group to join an event. Owner chooses which members will partake
	 * @param data
	 * @return
	 *//*
	@POST
	@Path("/joinEventGroup")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response joinEventGroup(EventData data) {
		LOG.info("Attempt to join event.");

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
			r = checkRBAC(txn, authToken.getString("role"), "joinEventGroup");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			Key userKey = userKeyFactory.newKey(authToken.getString("username").toLowerCase());
			Entity user = txn.get(userKey);

			if( data.name == null ) {
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventName);
			Entity event = txn.get(eventKey);

			//Check if event exists
			if( event == null ) {
				LOG.warning("Event doesn't exist.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			// Check if event is ongoing or finished.
			if( event.getLong("date_start") < System.currentTimeMillis() ) {
				LOG.warning("Event is not available anymore.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento já não está disponível.").build();
			}
				
			String groupName = data.groupName.replaceAll("\\s+", "").toLowerCase();
			Key groupKey = groupKeyFactory.newKey(groupName);
			Entity group = txn.get(groupKey);
			
			//Check if all group would fit.
			int afterJoinSize = event.getList("participants").size() + group.getList("participants").size();
			
			if( afterJoinSize > event.getLong("capacity") ) {
				LOG.warning("Event can't support whole group.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento não tem lugares suficientes para o grupo.").build();
			}

			List<Value<String>> oldParticipants = event.getList("participants");
			List<Value<String>> newParticipants = new ArrayList<Value<String>>();

			List<Value<String>> members = group.getList("participants");
			newParticipants = joinValueLists(oldParticipants, members);

			event = Entity.newBuilder(event)
					.set("participants", newParticipants)
					.build();

			// Add the event to the users' list of events.
			for( Value<String> v : members ) {			
				Key userKeyTemp = userKeyFactory.newKey(v.get().toLowerCase());
				Entity userTemp = txn.get(userKeyTemp);
				List<Value<String>> newEvents = addStringToListValuesString(user.getList("eventsJoined"), eventName);
				userTemp = Entity.newBuilder(userTemp)
						.set("eventsJoined", newEvents)
						.build(); 
				txn.put(userTemp);
				cacheSupport.cache.put(userTemp.getKey().getName(), userTemp);
			}
			
			txn.put(event);
			cacheSupport.cache.put(eventKey.getName(), event);
			cacheSupport.cache.put(groupKey.getName(), group);
			
			txn.commit();
			return Response.ok().entity("Grupo inscrito com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}*/
	
	
	/**
	 * Method used by a group owner to remove a group from a joined event.
	 * All users initially joined will be removed.
	 * @param data
	 * @return
	 *//*
	@POST
	@Path("/leaveEventGroup")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response leaveEventGroup(EventData data) {
		LOG.info("Attempt to leave event.");

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
			r = checkRBAC(txn, authToken.getString("role"), "leaveEventGroup");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			Key userKey = userKeyFactory.newKey(authToken.getString("username").toLowerCase());
			Entity user = txn.get(userKey);

			if( data.name == null ) {
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventName);
			Entity event = txn.get(eventKey);

			//Check if event exists in database
			if( event == null ) {
				LOG.warning("Event doesn't exist.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}

			// Check if event is started or finished.
			if( event.getLong("date_start") < System.currentTimeMillis() ) {
				LOG.warning("Event is not available anymore.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento está a decorrer ou já terminou, não pode cancelar a inscrição.").build();
			}
			
			String groupName = data.groupName.replaceAll("\\s+", "").toLowerCase();
			Key groupKey = groupKeyFactory.newKey(groupName);
			Entity group = txn.get(groupKey);
			
			List<Value<String>> oldParticipants = event.getList("participants");			
			List<Value<String>> newParticipants = new ArrayList<Value<String>>();
			
			List<Value<String>> members = group.getList("participants");
			
			newParticipants = removeValueLists(oldParticipants, group.getList("participants"));
			
			event = Entity.newBuilder(event)
					.set("participants", newParticipants)
					.build();

			// Remove the event from the users' list of events.
			for( Value<String> v : members ) {			
				Key userKeyTemp = userKeyFactory.newKey(v.get().toLowerCase());
				Entity userTemp = txn.get(userKeyTemp);
				List<Value<String>> newEvents = removeStringFromListValuesString(user.getList("eventsJoined"), eventName);
				userTemp = Entity.newBuilder(userTemp)
						.set("eventsJoined", newEvents)
						.build(); 
				txn.put(userTemp);
				cacheSupport.cache.put(userTemp.getKey().getName(), userTemp);
			}		
			
			txn.put(event);
			cacheSupport.cache.put(eventKey.getName(), event);
			
			txn.commit();
			return Response.ok().entity("Cancelou a inscrição do evento com sucesso.").build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}*/
	
	
	/**
	 * Method used by a user to join a public group.
	 * @param data - name, at
	 * @return response message with status
	 *//*
	@POST
	@Path("/joinGroup")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response joinGroup(GroupData data) {
		LOG.info("Attempt to join group.");

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
			r = checkRBAC(txn, authToken.getString("role"), "joinGroup");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			Key userKey = userKeyFactory.newKey(authToken.getString("username").toLowerCase());
			Entity user = txn.get(userKey);

			if( data.name == null ) {
				LOG.warning("Invalid data on join group.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do grupo inválido.").build();
			}
			
			String groupName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key groupKey = groupKeyFactory.newKey(groupName);
			Entity group = txn.get(groupKey);

			//Check if group exists
			if( group == null ) {
				LOG.warning("Group doesn't exist, on join group.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Grupo não existe.").build();
			}

			//Check if group is full.
			if( group.getList("participants").size() >= group.getLong("capacity") ) {
				LOG.warning("Group is full, on join group.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Grupo está cheio.").build();
			}

			//Check if user already in group
			List<Value<String>> oldParticipants = group.getList("participants");
			if( oldParticipants.contains(StringValue.of(authToken.getString("username"))) ) {
				LOG.warning("User already in group which he attempted to join.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Você já faz parte deste grupo.").build();
			}

			// Add the user to the list of participants.
			List<Value<String>> newParticipants	= addStringToListValuesString(oldParticipants, authToken.getString("username"));

			group = Entity.newBuilder(group)
					.set("participants", newParticipants)
					.build();

			// Add the group to the user's list of groups joined.
			List<Value<String>> newGroups = addStringToListValuesString(user.getList("groupsJoined"), groupName);

			user = Entity.newBuilder(user)
					.set("groupsJoined", newGroups)
					.build();

			txn.put(group, user);
			cacheSupport.cache.put(groupKey.getName(), group);
			cacheSupport.cache.put(userKey.getName(), user);
			
			txn.commit();
			
			LOG.info("User " + user.getKey().getName() + " joined group successfully: " + group.getKey().getName());
			return Response.ok().entity("Juntou-se ao grupo com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}*/
	
	
	/**
	 * Method used by a user to leave one of his joined groups
	 * @param data - name, at
	 * @return response message with status
	 *//*
	@POST
	@Path("/leaveGroup")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response leaveGroup(GroupData data) {
		LOG.info("Attempt to leave group.");

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
			r = checkRBAC(txn, authToken.getString("role"), "leaveGroup");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			Key userKey = userKeyFactory.newKey(authToken.getString("username").toLowerCase());
			Entity user = txn.get(userKey);

			if( data.name == null ) {
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do grupo inválido.").build();
			}
			
			String groupName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key groupKey = groupKeyFactory.newKey(groupName);
			Entity group = txn.get(groupKey);

			//Check if group exists
			if( group == null ) {
				LOG.warning("Group doesn't exist.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Grupo não existe.").build();
			}

			//Check if user already out of group
			List<Value<String>> oldParticipants = group.getList("participants");
			
			if( !oldParticipants.contains(StringValue.of(authToken.getString("username"))) ) {
				LOG.warning("User already doesn't belong in this group, on leave group.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Você não faz parte deste grupo.").build();
			}

			//Remove the user from the list of participants.
			List<Value<String>> newParticipants	= removeStringFromListValuesString(oldParticipants, authToken.getString("username"));

			group = Entity.newBuilder(group)
					.set("participants", newParticipants)
					.build();

			// Remove the group from the user's list of groups joined.
			List<Value<String>> newGroups = removeStringFromListValuesString(user.getList("groupsJoined"), groupName);

			user = Entity.newBuilder(user)
					.set("groupsJoined", newGroups)
					.build();

			txn.put(group, user);
			cacheSupport.cache.put(groupKey.getName(), group);
			cacheSupport.cache.put(userKey.getName(), user);
			
			txn.commit();
			
			LOG.info("User " + user.getKey().getName() + " left group successfully: " + group.getKey().getName());
			return Response.ok().entity("Abandonou o grupo com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}*/
	
	
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
	 * Check if a user is in a session by doing some checks on the authToken
	 * @param txn - active transaction from where this method was called 
	 * @param at - sent token, check if not null
	 * @param authToken - token from database, check validity
	 * @return response message with status
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
			return Response.status(Status.UNAUTHORIZED).entity("Auth Token expirado. Faça login antes de tentar outra vez.").build();
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
			txn.rollback();
			return Response.status(Status.BAD_REQUEST).entity("Método especificado não existe.").build();
		}
		
		//Check RBAC
		if( method.getBoolean(role) == false ) {
			LOG.warning("User doesn't have permission for this action.");
			txn.rollback();
			return Response.status(Status.UNAUTHORIZED).entity("Você não tem permissões para realizar esta tarefa.").build();
		} 
		else
			return Response.ok().build();
	}
	
	
	//!!! Methods from here on are required to work on database lists, since they are immutable
	
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
	
	
	/**
	 * Method used to join 2 lists of Values String
	 * @param list - list one
	 * @param toAdd - list two
	 * @return new list with both joined
	 */
	@SuppressWarnings("unused")
	private List<Value<String>> joinValueLists(List<Value<String>> list, List<Value<String>> toAdd) {
		List<Value<String>> listNew = new ArrayList<Value<String>>();
		
		for( Value<String> v : list ) 
			listNew.add(v);

		for( Value<String> v : toAdd ) 
			if( !listNew.contains(v) )
				listNew.add(v);

		return listNew;
	}
	
	/**
	 * Method used to remove a list of Value String from another list of Value String
	 * @param list - list from where to remove
	 * @param toRemove - list of values to remove
	 * @return new list with items removed
	 */
	@SuppressWarnings("unused")
	private List<Value<String>> removeValueLists(List<Value<String>> list, List<Value<String>> toRemove) {
		List<Value<String>> listNew = new ArrayList<Value<String>>();
		
		for( Value<String> v : list ) 
			listNew.add(v);

		for( Value<String> v : toRemove ) 
			listNew.remove(v);

		return listNew;
	}
	
	
}
