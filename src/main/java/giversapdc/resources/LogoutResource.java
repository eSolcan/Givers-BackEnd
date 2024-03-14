package giversapdc.resources;

import java.util.logging.Logger;

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
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;

import giversapdc.util.AuthToken;
import giversapdc.util.CacheSupport;
import giversapdc.util.UserData;

@Path("/logout")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LogoutResource {

	private static final Logger LOG = Logger.getLogger(LogoutResource.class.getName());
	
	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory authTokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");
	
	private CacheSupport cacheSupport = new CacheSupport();
	
	public LogoutResource() { }
	
	
	/**
	 * Used to logout a user from the application.
	 * Can be used on both web and android.
	 * @param data - at
	 * @return response message with status
	 */
	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response logoutUser(UserData data) {
		LOG.info("Attempt to logout user.");
		
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
	
			//Delete token from database and cache
			txn.delete(authToken.getKey());
			cacheSupport.cache.remove(authToken.getKey().getName());
			
			LOG.info("User " + authToken.getString("username") + " logged out successfully.");
			txn.commit();
			return Response.ok().entity("Logout com sucesso.").build();
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
	
	
}
