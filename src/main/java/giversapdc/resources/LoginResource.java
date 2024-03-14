package giversapdc.resources;

import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.codec.digest.DigestUtils;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;
import com.google.gson.Gson;

import giversapdc.util.AuthToken;
import giversapdc.util.CacheSupport;
import giversapdc.util.LoginData;

@Path("/login")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginResource {
	
	private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
	private final Gson g = new Gson();
	
	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
	private KeyFactory authTokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	
	private CacheSupport cacheSupport = new CacheSupport();
	
	//public static final long EXPIRATION_TIME = 24*60*60*1000; 	//24h in milliseconds
	
	public LoginResource() { }
	
	
	/**
	 * Method used to login a user to the app. Can be used both on web and android.
	 * If another login is detected ("at" not null), then the new login is aborted
	 * @param data - username and password
	 * @return authToken - indicates that a user is in a valid session
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
	public Response loginUser(LoginData data) {
		LOG.info("Login attempt by user.");
		
		Transaction txn = datastore.newTransaction();
		try {			
			//Initial check for session login
			if( data.at != null ) {
				LOG.warning("Attempt to login while already in a logged session.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Deve fazer logout antes de tentar fazer login novamente.").build();
			}
			
			//Check given data validity
			if( data.username == null || data.password == null  
					|| data.username.trim().equals("") || data.password.trim().equals("") ) {
				LOG.warning("Attempt to login with invalid data.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Dados inválidos.").build();
			}
			
			String username = data.username.replaceAll("\\s+", "").toLowerCase();
			Key userKey = userKeyFactory.newKey(username);
			Entity user = datastore.get(userKey);
			
			//Check if user exists in database
			if( user == null ) {
				LOG.warning("User does not exist on login attempt.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Utilizador não existe.").build();
			} 
			//Check if account state is active
			else if( user.getBoolean("state") == false ) {
				LOG.warning("Attempt to login disabled account.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Conta inativa.").build();
			}
			
			//Get DB hashed pass and check if it matches the given pass
			String hashedPass = (String) user.getString("password");
			if( !hashedPass.equals(DigestUtils.sha512Hex(data.password)) ) {
				LOG.warning("Incorrect password on login.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Username ou password incorretos.").build();
			}
			else {		
				//Return auth token to user and store in DB
				Key codeValueKey = codeValueKeyFactory.newKey("authtokenduration");
				Entity codeValue = datastore.get(codeValueKey);
				AuthToken token = new AuthToken(username, user.getString("role"), codeValue.getLong("value"));

				//Using tokenID as key, because if it were the username, could not have 2 different tokens for same user
				Key authTokenKey = authTokenKeyFactory.newKey(token.tokenID); 
				Entity authToken = txn.get(authTokenKey);
				authToken = Entity.newBuilder(authTokenKey)
						.set("username", token.username)
						.set("role", token.role)
						.set("creationDate", token.creationDate)
						.set("expirationDate", token.expirationDate)
						.build();
				
				cacheSupport.cache.put(authTokenKey.getName(), authToken);
				
				txn.add(authToken);
				txn.commit();
				LOG.info("User " + data.username + " logged in successfully.");
				return Response.ok(g.toJson(token)).build();
			}
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if(txn.isActive())
				txn.rollback();
		}
	}
	
	
}
