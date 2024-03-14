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

import com.google.appengine.repackaged.org.apache.commons.codec.digest.DigestUtils;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
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

import giversapdc.util.AuthToken;
import giversapdc.util.CacheSupport;
import giversapdc.util.EventData;
import giversapdc.util.InstitutionData;
import giversapdc.util.MapMarker;
import giversapdc.util.Pair;
import giversapdc.util.ShopItemData;
import giversapdc.util.UserData;

@Path("/edit")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class EditResource {

	//private static final String USER = "USER";
	private static final String SU = "SU";
	private static final String BO = "BO";
	
  /*private static final String PROFILE = "profile";
	private static final String INSTITUTION = "institution";
	private static final String EVENT = "event";
	private static final String GROUP = "group"; */
	
	private static final String PNG = "image/png";
	private static final String JPEG = "image/jpeg";
	
	private static final String PROJECT_ID = "givers-volunteering";
	private static final String BUCKET_ID = "givers-volunteering.appspot.com";
	private static final String BUCKET_URL = "https://storage.googleapis.com/givers-volunteering.appspot.com/";
	
	private static final String PROFILES = "profiles/";
	private static final String INSTITUTIONS = "institutions/";
	private static final String EVENTS = "events/";
	//private static final String GROUPS = "groups/";
	private static final String SHOP_ITEMS = "shopItems/";
	
	private static final String SUPPORT_EMAIL = "support@givers-volunteering.appspotmail.com";
	private static final String FORGOT_PASS_LINK = "https://givers-volunteering.appspot.com/forgotPassword?c=";
	
	
	private static final Logger LOG = Logger.getLogger(EditResource.class.getName());

	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
	private KeyFactory profileKeyFactory = datastore.newKeyFactory().setKind("Profile");
	private KeyFactory authTokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");
	private KeyFactory institutionKeyFactory = datastore.newKeyFactory().setKind("Institution");
	private KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind("Event");
	//private KeyFactory groupKeyFactory = datastore.newKeyFactory().setKind("Group");
	private KeyFactory markerKeyFactory = datastore.newKeyFactory().setKind("Marker");
	private KeyFactory notificationKeyFactory = datastore.newKeyFactory().setKind("Notification");
	private KeyFactory shopItemKeyFactory = datastore.newKeyFactory().setKind("Shop");
	private KeyFactory resetPasswordKeyFactory = datastore.newKeyFactory().setKind("ResetPassword");
	private KeyFactory rbacKeyFactory = datastore.newKeyFactory().setKind("AccessControl");
		
	private CacheSupport cacheSupport = new CacheSupport();

	public EditResource() { }

	/**
	 * Method used by a user to edit his own profile
	 * @param data - phoneNr, dateOfBirth, gender, nationality, address, interests, description, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/profile")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editProfile(UserData data) {
		LOG.info("Attempt to edit profile.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editProfile");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			String username = authToken.getString("username");
			Key profileKey = profileKeyFactory.addAncestors(PathElement.of("User", username)).newKey(username);
			Entity profile = txn.get(profileKey);
	
			//Validate given data
			r = data.validDataEdit(txn);
			if( r.getStatus() != 200 ) {
				LOG.warning("Invalid data on edit profile. Username " + username + "; error: " + r.getEntity());
				txn.rollback();
				return r;
			}
			
			//This is required, because there was a weird way to circumvent our checks
			String phoneNrTemp = data.phoneNr;
			phoneNrTemp = phoneNrTemp.replaceAll("[^\\d.]", "");
			
			// Rebuild the profile, with old data + new data
			profile = Entity.newBuilder(profile)
					.set("interests", converToValueList(data.interests))
					.set("phoneNr", phoneNrTemp)
					.set("dateOfBirth", data.dateOfBirth)
					.set("gender", data.gender)
					.set("nationality", data.nationality)
					.set("address", data.address)
					.set("description", data.description)
					.build();
			
			txn.put(profile);
			cacheSupport.cache.put(username + "profile", profile);
			
			txn.commit();
			
			LOG.info("Profile edited successfully.");
			return Response.ok().entity("Perfil editado com sucesso.").build();
			
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
	 * Method used by a user to edit own profile photo.
	 * @param data - photo, at
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/photoProfile")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editPhotoProfile(UserData data) {
		LOG.info("Attempt to edit profile photo.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editPhotoProfile");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			String username = authToken.getString("username");
			Key profileKey = profileKeyFactory.addAncestors(PathElement.of("User", username)).newKey(username);
			Entity profile = txn.get(profileKey);
						
			//Photo should never come null, but just in case, do this check
			if( data.photo == null ) {
				LOG.warning("Attempt to edit photo profile with null photo.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Foto não pode ser null.").build();
			}
			else if( data.photo.length != 0 ) {
				//Upload photo to cloud storage 
				String fileName = PROFILES + authToken.getString("username") + System.currentTimeMillis();
				String fileLink = BUCKET_URL + fileName;

				//Delete old photo first, if not default
				if( profile.getString("photo").contains(PROFILES) ){
					String oldFileName = PROFILES + profile.getString("photo").split(PROFILES)[1];
					Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
					storage.delete(BUCKET_ID, oldFileName);
				}
				
				r = uploadPhoto(txn, fileName, data.photo);
				if( r.getStatus() != 200 )
					return r;

				//Rebuild the profile with new photo link
				profile = Entity.newBuilder(profile)
						.set("photo", fileLink)
						.build();
				
				txn.put(profile);
				cacheSupport.cache.put(username + "profile", profile);
				
				txn.commit();
				
				LOG.info("Profile photo edited successfully: " + username);
				return Response.ok().entity(new Pair("Foto de perfil editada com sucesso.", fileLink)).build();
			} else {
				txn.rollback();
				LOG.warning("Attempt to edit profile photo with no bytes");
				return Response.ok().entity("Não introduziu uma foto.").build();
			}
				
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
	 * Method used by a user to change own password.
	 * Must provide old password, and new password twice.
	 * @param data - oldPassword, password, passwordConfirm, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/password")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editPassword(UserData data) {
		LOG.info("Attempt to edit password.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editPassword");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			Key userKey = userKeyFactory.newKey(authToken.getString("username").toLowerCase());
			Entity user = txn.get(userKey);
			
			//Check if provided old password is correct
			if( data.oldPassword != null && !user.getString("password").equals(DigestUtils.sha512Hex(data.oldPassword))) {
				LOG.warning("Incorrect old password on edit password.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password antiga incorreta.").build();
			}
			
			//Check if given data is valid
			if( data.password == null || data.passwordConfirm == null 
					|| data.password.replaceAll("\\s+", "").equals("") || data.passwordConfirm.replaceAll("\\s+", "").equals("")) {
				LOG.warning("Attempt to change password with invalid data.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nova password ou confimação inválida").build();
			}
			
			//Validate new password
			if( !data.validPassword() ) {
				LOG.warning("Invalid data on edit password.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nova password deve ter pelo menos 5 caracteres.").build();
			} 
			
			//Check if both new password and confirmation are equal
			if( !DigestUtils.sha512Hex(data.password).equals(DigestUtils.sha512Hex(data.passwordConfirm)) ) {
				LOG.warning("Incorrect confirmation on edit password.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Confirmação da password incorreta.").build();
			}

			//Update user
			user = Entity.newBuilder(user)
					.set("password", DigestUtils.sha512Hex(data.password))
					.build(); 

			txn.put(user);
			cacheSupport.cache.put(userKey.getName(), user);
			
			txn.commit();
			
			LOG.info("Password changed successfully for: " + user.getString("username"));
			return Response.ok().entity("Password editada com sucesso.").build();
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
	 * Method used by a user to request a "change password" in case he has forgotten his previous one
	 * A message will be sent to the given email that will redirect to the page where the password can be changed
	 * @param data - email
	 * @return response message with status
	 */
	@POST
	@Path("/forgotPasswordMail")
	public Response requestForgotPassword(UserData data) {
		LOG.info("Attempt to request forgot password email.");

		//Check email format
		if( !data.validEmail() ) {
			LOG.warning("Attempt to request forgot password mail with invalid email format.");
			return Response.status(Status.BAD_REQUEST).entity("Email deve ter o seguinte formato *****@*****.*** .").build();
		}
		
		//Check first if given email has an account associated
		Query<Entity> query = Query.newEntityQueryBuilder()
				.setKind("User")
				.setFilter(PropertyFilter.eq("email", data.email.toLowerCase()))
				.build();
		QueryResults<Entity> tasks = datastore.run(query);
		
		Entity user = null;
		if( tasks.hasNext() )
			user = tasks.next();
		
		if( user == null ) {
			LOG.warning("Attempt to request forgot password mail with unexistent email.");
			return Response.status(Status.BAD_REQUEST).entity("Dados inválidos. Verifique se o mail que introduziu está correto.").build();
		}
		
		String email = data.email.replaceAll("\\s+", "").toLowerCase();
		
		//Add an entry to the database indicating that this email requested a reset password
		Key resetKey = resetPasswordKeyFactory.newKey(email);
		Entity reset = datastore.get(resetKey);
		
		if( reset != null ) {
			LOG.warning("Attempt to request reset password email with already requested.");
			return Response.status(Status.FORBIDDEN).entity("Já fez um pedido com este email. Verifique a sua inbox (ou spam).").build();
		}
		
		//Create random 6 digit voucher code
		Random rnd = new Random();
		int nrRnd = 100000 + rnd.nextInt(900000);
		
		reset = Entity.newBuilder(resetKey)
				.set("email", email)
				.set("code", String.valueOf(nrRnd))
				.build();
		
		datastore.put(reset);
		
		String link = "\"" + FORGOT_PASS_LINK + nrRnd + "\"";
		
		//Configure properties
		Properties props = new Properties();
		
		//Account from where to send email
		String from = SUPPORT_EMAIL;
		
		//Account to where to send mail
		String to = data.email;
		
		//Create session
		Session session = Session.getInstance(props, null);
		
		try {
			//Build message
			String msg = "<p style=\"font-family: Arial\">Olá!</p>\r\n"
					+ "<br>\r\n"
					+ "<p style=\"font-family: Arial\">Pode alterar a sua password seguindo <a href=" + link + ">este link</a></p>\r\n"
					+ "<br>\r\n"
					+ "<p style=\"font-family: Arial\">Caso não tenha realizado este pedido, ignore este mail.</p>\r\n"
					+ "<br>\r\n"
					+ "<p style=\"font-family: Arial\">Obrigado,</p>\r\n"
					+ "<p style=\"font-family: Arial\">Equipa NovaWare</p>";
			
			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(from));
			message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
			message.setSubject("Givers Volunteering Support - Password");
			message.setContent(msg, "text/html");

			//Send mail
			Transport.send(message);
			
			LOG.info("Change password mail sent successfully.");
			return Response.ok().entity("Mail enviado com sucesso.").build();
		} catch( Exception e ) {			
			LOG.severe("Failed to send mail: " + e.getMessage());
			return Response.status(Status.BAD_REQUEST).entity("Erro no envio de mail." ).build();
		}
	}
	
	
	/**
	 * Method used by a user to change his password after requesting a "forgot password"
	 * To validate his identity, user must provide the username of the account, the email and the new password and confirmation
	 * @param data - code, username, email, password, passwordConfirm
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/forgotPassword")
	public Response forgotPassword(UserData data) {
		LOG.info("Attempt to change password via forgot password.");

		Transaction txn = datastore.newTransaction();
		try {
			//Check if given email is valid
			if( data.email == null || data.email.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to reset password with invalid email.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Email inválido.").build();
			}
			
			//Check if given username is valid
			if( data.username == null || data.username.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to reset password with invalid username.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Username inválido.").build();
			}
			
			String email = data.email.replaceAll("\\s+", "").toLowerCase();
			Key userKey = userKeyFactory.newKey(data.username.replaceAll("\\+s", "").toLowerCase());
			Entity user = txn.get(userKey);

			//Check if account with given email has requested a reset password
			Key resetKey = resetPasswordKeyFactory.newKey(email);
			Entity reset = txn.get(resetKey);
			if( reset == null ) {
				txn.rollback();
				LOG.warning("Attempt to reset a password for unrequested email");
				return Response.status(Status.BAD_REQUEST).entity("Dados inválidos.").build();
			}
			
			//Check if verification code is correct
			if( data.code == null || !data.code.equals(reset.getString("code")) ) {
				LOG.warning("Attempt to reset password with incorrect code.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Código incorreto.").build();
			}
			
			//Check if user exists in database. If so, check if given mail is correct
			if( user == null ) {
				LOG.warning("User doesn't exist on forgot password.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Dados inválidos.").build();
			}
			else if( !user.getString("email").equalsIgnoreCase(data.email.replaceAll("\\s+", "")) ) {
				LOG.warning("Incorrect mail on forgot password.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Dados inválidos.").build();
			}
			
			//Check if password and confirm are valid
			if( data.password == null || data.passwordConfirm == null
					|| data.password.replaceAll("\\s+", "").equals("") || data.passwordConfirm.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to reset password with invalid password or confirmation.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password ou confirmação inválida.").build();
			}
			
			//Check password validity
			if( !data.validPassword() ) {
				LOG.warning("Attempt to reset password with invalid password.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password deve conter pelo menos 5 caracteres.").build();
			}
			
			//Check if given password and confirmation are equal
			if( !data.password.equals(data.passwordConfirm) ) {
				LOG.warning("Given password and confirmation are not equal in forgot password.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password e confirmação não são iguais.").build();
			}
			
			//Update user password
			user = Entity.newBuilder(user)
					.set("password", DigestUtils.sha512Hex(data.password))
					.build(); 
			
			//Get user's auth tokens
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("AuthToken")
					.setFilter(PropertyFilter.eq("username", user.getString("username")))
					.build();
			QueryResults<Entity> tasks = txn.run(query);
			
			//Delete all user's active tokens
			tasks.forEachRemaining(autTok -> { 	
				txn.delete(autTok.getKey());
				cacheSupport.cache.remove(autTok.getKey());
			});
			
			txn.delete(resetKey);
			txn.put(user);
			cacheSupport.cache.put(userKey.getName(), user);
			
			txn.commit();
			
			LOG.info("Password changed successfully on user: " + data.username);
			return Response.ok().entity("Password da conta editada com sucesso.").build();

		} catch (Exception e) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
		
	}
	
	
	/*
	 * Method that calls the rest service to edit a user's role. Users can't change
	 * their own role, and only higher roles can edit lower ones.
	 * When a role is changed, all active tokens of that user are expired.
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/profile/role")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editRole(UserData data) {
		LOG.info("Attempt to edit user role.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editRole");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check given username validity
			if( data.username == null || data.username.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to change role with invalid username.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Username inválido.").build();
			}
			
			String usernameOp = authToken.getString("username");			
			Key userKey = userKeyFactory.newKey(data.username.toLowerCase());
			Entity user = txn.get(userKey);
			
			// Check if user exists in the database.
			if( user == null ) {
				LOG.warning("User doesn't exist.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("User que quer editar não existe.").build();
			}
			
			//Check if user is attempting to change role on own account
			if( user.getString("username").equalsIgnoreCase(usernameOp) ) {
				LOG.warning("Attempt to change role on own account.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não pode alterar o Role da sua própria conta.").build();
			}
			
			//Check if given role is valid
			if( data.role == null ) {
				LOG.warning("Attempt to edit role with invalid role.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Role inválido.").build();
			}
			
			String roleNew = data.role.replaceAll("\\s+", "").toUpperCase();
			String roleOp = authToken.getString("role");
			String roleOld = user.getString("role");
			
			//  Fail if: 
			// 		BO attempts to edit something to BO or SU
			// 		BO attempts to edit BO or SU to something else
			if( ((roleNew.equals(BO) || roleNew.equals(SU) ) && roleOp.equals(BO)) 
					|| ( roleOld.equals(BO) && roleOp.equals(BO) )) {
				LOG.warning("Attempt to do invalid role change.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Você não tem permissões para realizar essa alteração.").build();
			} 

			user = Entity.newBuilder(user)
					.set("role", roleNew)
					.build(); 
			
			//Get user's auth tokens
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("AuthToken")
					.setFilter(PropertyFilter.eq("username", user.getString("username")))
					.build();
			QueryResults<Entity> tasks = txn.run(query);
			
			//Delete all user's active tokens
			tasks.forEachRemaining(autTok -> { 	
				txn.delete(autTok.getKey());
				cacheSupport.cache.remove(autTok);
			});

			txn.put(user);
			cacheSupport.cache.put(userKey.getName(), user);
			
			txn.commit();
			return Response.ok().entity("Role editado com sucesso.").build();
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
	 * Method used by a BO or SU user to change another user's account state (active/inactive toggle)
	 * @param data - username, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/profile/state")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editState(UserData data) {
		LOG.info("Attempt to edit user state.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editState");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check if given username is valid
			if( data.username == null || data.username.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to edit state with invalid username.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Username inválido.").build();
			}
			
			Key userKey = userKeyFactory.newKey(data.username.toLowerCase());
			Entity user = txn.get(userKey);
			
			//Check if user exists in the database.
			if( user == null ) {
				LOG.warning("User doesn't exist.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("User não existe.").build();
			}

			//Check if user is attempting to change state on own account
			if( user.getString("username").equalsIgnoreCase(authToken.getString("username")) ) {
				LOG.warning("Attempt to change state on own account.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não pode desativar a sua própria conta.").build();
			}
			
			String roleOp = authToken.getString("role");
			String roleUser = user.getString("role");
			
			//BO users can't change state on other BO or SU
			if( roleOp.equals(BO) && (roleUser.equals(BO) || roleUser.equals(SU)) ) {
				LOG.warning("Attempt to change state on user of same or higher role.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Você não tem permissões para alterar estados de contas BO ou SU.").build();
			}
			
			boolean newState = !user.getBoolean("state");
			
			//Update user state
			user = Entity.newBuilder(user)
					.set("state", newState)
					.build(); 
			
			//If newState is false, means account became inactive, so we delete user's active auth tokens
			if( newState == false ) {
				Query<Entity> query = Query.newEntityQueryBuilder()
						.setKind("AuthToken")
						.setFilter(PropertyFilter.eq("username", user.getString("username")))
						.build();
				QueryResults<Entity> tasks = txn.run(query);
				
				tasks.forEachRemaining(autTok -> { 
					txn.delete(autTok.getKey());
					cacheSupport.cache.remove(autTok);
				});
			}
			
			txn.put(user);
			cacheSupport.cache.put(userKey.getName(), user);
			
			txn.commit();
			
			String response = "";
			if( user.getBoolean("state") ) {
				response = "Conta ativada com sucesso.";
			}
			else
				response = "Conta desativada com sucesso.";
			
			LOG.info("State changed successfully on user: " + data.username);
			return Response.ok().entity(response).build();

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
	 * Method used to change information about an institution, by its owner.
	 * @param data - email, phoneNr, address, photo, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/institution")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editInstitution(InstitutionData data) {
		LOG.info("Attempt to edit institution info.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editInstitution");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Get user's institution
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("Institution")
					.setFilter(PropertyFilter.eq("owner", authToken.getString("username")))
					.build(); 
			
			QueryResults<Entity> tasks = datastore.run(query);
			
			//Check if institution exists in database
			Entity institution = null;
			if( tasks.hasNext() )
				institution = tasks.next();
			else {
				LOG.warning("Attempt to edit institution with no owned inst.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Não é representante de nenhuma instituição.").build();
			}

			//Verify new data
			Response re = data.validDataEdit(txn);
			if( re.getStatus() != 200 ) {
				LOG.warning("Invalid data on edit institution. " + re.getEntity());
				txn.rollback();
				return re;
			}
			
			//Edit institution and upload to database
			institution = Entity.newBuilder(institution)
					.set("email", data.email)
					.set("phoneNr", data.phoneNr) 
					.set("address", data.address)
					.build();
			
			//If user uploaded photo, edit that as well
			//Photo should never come null, but just in case, do this check
			if( data.photo == null ) {
				LOG.warning("Attempt to edit inst with null photo.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Foto não pode ser null.").build();
			}
			else if( data.photo.length != 0 ) {
				//Upload photo to cloud storage 
				String fileName = INSTITUTIONS + institution.getString("name").replaceAll("\\s+", "").toLowerCase() + System.currentTimeMillis();
				String fileLink = BUCKET_URL + fileName;
				
				//Delete old photo first, if not default
				if( institution.getString("photo").contains(INSTITUTIONS) ){
					String oldFileName = INSTITUTIONS + institution.getString("photo").split(INSTITUTIONS)[1];
					Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
					storage.delete(BUCKET_ID, oldFileName);
				}
				
				r = uploadPhoto(txn, fileName, data.photo);
				if( r.getStatus() != 200 )
					return r;
				
				//Rebuild the institution with new photo link
				institution = Entity.newBuilder(institution)
						.set("photo", fileLink)
						.build();
			}
			
			txn.put(institution);
			cacheSupport.cache.put(institution.getKey().getName(), institution);
			
			txn.commit();
			
			LOG.info("Institution edited successfully: " + data.name);
			return Response.ok().entity("Instituição editada com sucesso.").build();
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
	 * Method used by an inst owner to edit one of their events.
	 * Events can only be edited if they aren't ongoing, and if start date is farther than 24h away.
	 * Participating users will receive a notification regarding the edit of the event.
	 * @param data - name, interests, address, dateStart, duration, capacity, description, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/event")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editEvent(EventData data) {
		LOG.info("Attempt to edit event.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editEvent");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Invalid event name on edit event.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventName);
			Entity event = txn.get(eventKey);
			
			// Check if event with given name exists in the database
			if( event == null ) {
				LOG.warning("Event doesn't exist on event edit.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			String instName = event.getString("institution").toLowerCase();
			Key instKey = institutionKeyFactory.newKey(instName);
			Entity inst = txn.get(instKey);

			// Check if user editing is owner of institution that's hosting the event.
			if( !inst.getString("owner").toLowerCase().equals(authToken.getString("username")) ) {
				LOG.warning("User is not owner on edit event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não tem permissão para gerir eventos desta instituição.").build();
			}

			//Check if event is ongoing or already ended => can't edit
			if( event.getLong("date_start") < System.currentTimeMillis()) {
				LOG.warning("Attempt to edit ongoing or ended event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento está a decorrer ou já terminou, não pode ser editado.").build();
			}
			
			//Check validity of given data
			r = data.validDataEdit();
			if( r.getStatus() != 200 ) {
				LOG.warning("Invalid event data on edit event. " + r.getEntity());
				txn.rollback();
				return r;
			}
			
			// Check if the new capacity is bigger than the number of participants
			if( data.capacity != 0 && data.capacity < event.getLong("joinedUsersCount") ) {
				LOG.warning("New capacity is smaller than the number of participants on event edit.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nova capacidade não pode ser menor que o número de participantes já inscritos.").build();
			}
			
			//Parse duration, comes in string of this format HH:MM
			int hours = Integer.parseInt(data.duration.split(":")[0]);
			int minutes = Integer.parseInt(data.duration.split(":")[1]);
			Long duration = (long) ((hours*60 + minutes) * 60 * 1000);
			
			// Rebuild the event
			event = Entity.newBuilder(event)
					.set("interests", data.interests.replaceAll("\\s+", "").toLowerCase())
					.set("address", data.address)
					.set("lat", data.markers[0].lat)
					.set("lon", data.markers[0].lng)
					.set("date_start", data.dateStart)
					.set("date_end", data.dateStart + duration)
					.set("capacity", data.capacity)
					.set("description", data.description)
					.build();

			//This is the best that I could come up with, I'm sure there's better ways out there, but this will do for now
			//Should be fine, because editing a course shouldn't be done too many times, so the costs won't pile up over time, hopefully
			
			//Query to get all event route markers
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("Marker")
					.setFilter(PropertyFilter.hasAncestor(eventKey))
					.build();
			QueryResults<Entity> tasks = txn.run(query);

			//Delete all previous markers
			tasks.forEachRemaining(mrkr -> { 	
				txn.delete(mrkr.getKey());
			});
			
			//Add all new route markers
			KeyFactory mkKeyFactory = markerKeyFactory.addAncestor(PathElement.of("Event", eventName.toLowerCase()));
			Key markerKey;
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
				
				Entity marker = Entity.newBuilder(markerKey)
						.set("lat", mrkr.lat)
						.set("lon", mrkr.lng)
						.set("eventName", data.name)
						.set("eventId", eventName)
						.set("description", mrkr.description)
						.set("photos", new ArrayList<Value<String>>())
						.set("risk", mrkr.risk)
						.build();
				txn.put(marker);
				i++;
			}
			
			//If user uploaded photo, edit that as well
			//Photo should never come null, but just in case, do this check
			if( data.photo == null ) {
				LOG.warning("Attempt to edit event with null photo.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Foto não pode ser null.").build();
			}
			else if( data.photo.length != 0 ) {
			
				//Upload photo to cloud storage
				String fileName = EVENTS + eventName + "/" + eventName + System.currentTimeMillis();
				String fileLink = BUCKET_URL + fileName;
				
				//Delete old photo first, if not default
				if( event.getString("photo").contains(EVENTS) ){
					String oldFileName = EVENTS + event.getString("photo").split(EVENTS)[1];
					Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
					storage.delete(BUCKET_ID, oldFileName);
				}
				
				r = uploadPhoto(txn, fileName, data.photo);
				if( r.getStatus() != 200 )
					return r;
				
				//Rebuild the event with new photo link
				event = Entity.newBuilder(event)
						.set("photo", fileLink)
						.build();
			}
			
			String eventId = event.getKey().getName();
			
			//Check if the event has any participants. If so, create notifications for those users
			query = Query.newEntityQueryBuilder()
					.setKind("UserEventsJoined")
					.setFilter(PropertyFilter.eq("eventId", eventId))
					.build(); 
			
			tasks = datastore.run(query);
			
			tasks.forEachRemaining(participation -> {
				String usernameTemp = participation.getString("username");
				
				//Create notification
				Key notificationKey = notificationKeyFactory.newKey(usernameTemp + eventId);
				Entity notification = Entity.newBuilder(notificationKey)
						.set("username", usernameTemp)
						.set("text", "Evento " + data.name + " em que estava inscrito foi editado.")
						.set("delivered", false)
						.build();
				txn.put(notification);
				
				//Delete participation
				txn.delete(participation.getKey());
			});

			//Update event in cache and database
			txn.put(event);
			cacheSupport.cache.put(eventKey.getName(), event);
			
			txn.commit();
			
			LOG.info("Event edited successfully: " + eventName);
			return Response.ok().entity("Evento editado com sucesso.").build();
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

		BlobId blobId = BlobId.of(BUCKET_ID, fileName);
		BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("image/png").build();
							
		storage.create(blobInfo, photo);
		return Response.ok().build();
	}
	
	
	/**
	 * Used by a BO or SU user to edit an existent shop item.
	 * @param data - itemName, pricePer, quantity, description, at
	 * @return response message with status
	 */
	@POST
	@Path("/shopItem")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editShopItem(ShopItemData data) {
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
			r = checkRBAC(txn, authToken.getString("role"), "editShopItem");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Check if given item name is valid
			if( data.itemName == null || data.itemName.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to edit shop item with invalid item name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do item inválido.").build();
			}

			Key shopItemKey = shopItemKeyFactory.newKey(data.itemName.replaceAll("\\s+", "").toLowerCase());
			Entity shopItem = txn.get(shopItemKey);
			
			//Check if item exits in database
			if( shopItem == null ) {
				LOG.warning("Attempt to edit unexistent shop item.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Não existe um item com esse nome.").build();
			}
			
			//Check data validity
			if( data.validDataEdit(txn).getStatus() != 200 ) {
				LOG.warning("Invalid data on edit shop item. " + data.validDataEdit(txn).getEntity());
				txn.rollback();
				return data.validDataEdit(txn);
			}
	
			//Edit shop item
			shopItem = Entity.newBuilder(shopItem)
					.set("pricePer", data.pricePer)
					.set("quantity", data.quantity)
					.set("description", data.description)
					.build();
			
			//If photo was added, upload photo and store link, otherwise use default image
			String fileName = "";
			
			//If user uploaded photo, edit that as well
			//Photo should never come as null, because web sends empty array, but just in case check
			if( data.photo == null ) {
				LOG.warning("Attempt to edit item with null photo.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Foto não pode ser null.").build();
			}
			else if( data.photo.length != 0 ) {
				//Upload photo to cloud storage 
				fileName = SHOP_ITEMS + shopItemKey.getName() + System.currentTimeMillis();
				String fileLink = BUCKET_URL + fileName;

				//Delete old photo first, if not default
				if( shopItem.getString("photo").contains(SHOP_ITEMS) ){
					String oldFileName = SHOP_ITEMS + shopItem.getString("photo").split(SHOP_ITEMS)[1];
					Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
					storage.delete(BUCKET_ID, oldFileName);
				}
				
				r = uploadPhoto(txn, fileName, data.photo);
				if( r.getStatus() != 200 )
					return r;
				
				//Add photo link to shop item
				shopItem = Entity.newBuilder(shopItem)
						.set("photo", fileLink)
						.build();
			}

			txn.put(shopItem);
			txn.commit();
			
			LOG.info("Edited shop item successfully: " + shopItem.getKey().getName());
			return Response.ok().entity("Item editado com sucesso.").build();
			
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
	 * Method used to edit a group by its owner
	 * @param data - name, capacity, description, at
	 * @return response message with status
	 *//*
	@POST
	@Path("/group")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editGroup(GroupData data) {
		LOG.info("Attempt to edit group.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editGroup");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			if( data.name == null ) {
				LOG.warning("Invalid group name on edit group.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do grupo inválido.").build();
			}
			
			String groupName = data.name.replaceAll("\\s+", "");
			Key groupKey = groupKeyFactory.newKey(groupName.toLowerCase());
			Entity group = txn.get(groupKey);
			
			// Check if group with given name exists in the database
			if( group == null ) {
				LOG.warning("Group doesn't exist on group edit.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Grupo não existe.").build();
			}
			
			// Check if user editing is owner of group
			if( !group.getString("owner").equals(authToken.getString("username").toLowerCase()) ) {
				LOG.warning("User is not owner on group edit.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Você não tem permissão para gerenciar este grupo.").build();
			}
			
			r = data.validDataEdit();
			if( r.getStatus() != 200 ) {
				LOG.warning("Invalid data on group edit.");
				txn.rollback();
				return r;
			}
			
			// Check if the new capacity is bigger than the number of participants
			if( data.capacity != 0 && data.capacity < group.getList("participants").size() ) {
				LOG.warning("New capacity is smaller than the number of participants on group edit.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nova capacidade é menor que o número de membros do grupo.").build();
			}

			//Edit group and add to database
			group = Entity.newBuilder(group)
					.set("capacity", data.capacity)
					.set("description", data.description)
					.build();

			txn.put(group);
			cacheSupport.cache.put(groupKey, group);
			
			txn.commit();
			
			LOG.info("Group edited successfully: " + group.getKey().getName());
			return Response.ok().entity("Grupo editado com sucesso.").build();
		} catch (Exception e) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if (txn.isActive())
				txn.rollback();
		}
	}*/
	
	
	/**
	 * Method used by the owner of a group to change its photo
	 * @param data
	 * @return
	 *//*
	@POST
	@Path("/photoGroup")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response editPhotoGroup(EventData data) {
		LOG.info("Attempt to edit group photo.");

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
			r = checkRBAC(txn, authToken.getString("role"), "editPhotoGroup");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			String username = authToken.getString("username");

			Key groupKey = groupKeyFactory.newKey(data.name.replaceAll("\\s+", "").toLowerCase());
			Entity group = txn.get(groupKey);

			//Check if event with given name exists in the database
			if( group == null ) {
				LOG.warning("Group doesn't exist on edit group photo.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			//Check if user attempting edit is owner of group
			if( !group.getString("owner").equals(username) ) {
				LOG.warning("User is not owner on edit group photo.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não é o dono deste grupo, não pode editar a foto.").build();
			}
			
			//Upload photo to cloud storage 
			String fileName = GROUPS + group.getString("name").replaceAll("\\s+", "").toLowerCase() + System.currentTimeMillis();
			String fileLink = BUCKET_URL + fileName;
			try {
				//Delete old photo first, if not default
				if( group.getString("photo").contains(GROUPS) ){
					String oldFileName = GROUPS + group.getString("photo").split(GROUPS)[1];
					Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
					storage.delete(BUCKET_ID, oldFileName);
				}
				
				r = uploadPhoto(txn, fileName, data.photo);
				if( r.getStatus() != 200 )
					return r;
			} catch(Exception e) {
				txn.rollback();
				LOG.severe(e.getMessage());
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro na leitura do ficheiro.").build();
			}
			
			//Rebuild the event with new photo link
			group = Entity.newBuilder(group)
					.set("photo", fileLink)
					.build();
			
			txn.put(group);
			cacheSupport.cache.put(groupKey.getName(), group);
			
			txn.commit();
			
			LOG.info("Edit group photo successful: " + group.getKey().getName());
			return Response.ok().entity(new Pair("Foto do grupo editada com sucesso.", fileLink)).build();
			
		} catch (Exception e) {
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
			txn.rollback();
			return Response.status(Status.NOT_FOUND).entity("Método especificado não existe.").build();
		}
		
		//Check RBAC
		if( method.getBoolean(role) == false ) {
			LOG.warning("User doesn't have permission for this action.");
			txn.rollback();
			return Response.status(Status.FORBIDDEN).entity("Você não tem permissão para realizar tal ação.").build();
		} 
		else
			return Response.ok().build();
	}
	
	
	/*
	 * Method used to convert a list of strings into a list of Values of Strings
	 */
	private List<Value<String>> converToValueList(List<String> list){
		List<Value<String>> newList = new ArrayList<Value<String>>();
		
		for( String s : list )
			newList.add(StringValue.of(s));
			
		return newList;
	}

}
