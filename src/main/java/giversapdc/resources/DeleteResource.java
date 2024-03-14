package giversapdc.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.gson.Gson;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;

import giversapdc.util.AuthToken;
import giversapdc.util.CacheSupport;
import giversapdc.util.CommentData;
import giversapdc.util.EventData;
//import giversapdc.util.GroupData;
import giversapdc.util.InstitutionData;
import giversapdc.util.ShopItemData;
import giversapdc.util.UserData;

@Path("/delete")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class DeleteResource {

	private static final String USER = "USER";
	private static final String INST_OWNER = "INST_OWNER";
	private static final String GROUP_OWNER = "GROUP_OWNER";
	private static final String BO = "BO";
	private static final String SU = "SU";
		
	private static final String PROJECT_ID = "givers-volunteering";
	private static final String BUCKET_ID = "givers-volunteering.appspot.com";
	private static final String PROFILES = "profiles/";
	private static final String INSTITUTIONS = "institutions/";
	private static final String EVENTS = "events/";
	//private static final String GROUPS = "groups/";
	private static final String SHOP_ITEMS = "shopItems/";
	
	//private static final String BUCKET_URL = "https://storage.googleapis.com/givers-volunteering.appspot.com/";
	
	//private static final String DEFAULT_USER_IMG = "https://storage.googleapis.com/givers-volunteering.appspot.com/defaultImages/userDefault.jpg";
	//private static final String DEFAULT_INST_IMG = "https://storage.googleapis.com/givers-volunteering.appspot.com/defaultImages/instDefault.jpg";
	//private static final String DEFAULT_EVENT_IMG = "https://storage.googleapis.com/givers-volunteering.appspot.com/defaultImages/eventDefault.jpg";
	//private static final String DEFAULT_GROUP_IMG = "https://storage.googleapis.com/givers-volunteering.appspot.com/defaultImages/groupDefault.jpg";
	
	private static final Logger LOG = Logger.getLogger(DeleteResource.class.getName());
	private final Gson g = new Gson();
	
	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
	private KeyFactory profileKeyFactory = datastore.newKeyFactory().setKind("Profile");
	private KeyFactory authTokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");
	private KeyFactory institutionKeyFactory =  datastore.newKeyFactory().setKind("Institution");
	private KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind("Event");
	//private KeyFactory groupKeyFactory = datastore.newKeyFactory().setKind("Group");
	private KeyFactory notificationKeyFactory = datastore.newKeyFactory().setKind("Notification");
	private KeyFactory logKeyFactory = datastore.newKeyFactory().setKind("Log");
	private KeyFactory commentKeyFactory = datastore.newKeyFactory().setKind("Comment");
	private KeyFactory shopItemKeyFactory = datastore.newKeyFactory().setKind("Shop");
	//private KeyFactory photoMarkerLogKeyFactory = datastore.newKeyFactory().setKind("PhotoMarkerLog");
	private KeyFactory rbacKeyFactory = datastore.newKeyFactory().setKind("AccessControl");
	private KeyFactory savedEventKeyFactory = datastore.newKeyFactory().setKind("SavedEvent");
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	
	private CacheSupport cacheSupport = new CacheSupport();
	
	public DeleteResource() { }
	
	
	/**
	 * Method used to delete own account from the application.
	 * @param data - password, passwordConfirm, at
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@DELETE
	@Path("/user")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deleteUser(UserData data) {
		LOG.info("Attempt to delete account.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "deleteUser");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			String username = authToken.getString("username");
			Key userKey = userKeyFactory.newKey(username);
			Key profileKey = profileKeyFactory.addAncestors(PathElement.of("User", username)).newKey(username);
			Entity user = txn.get(userKey);
			Entity profile = txn.get(profileKey);
			
			//Check if given password and confirmation are not null or empty strings
			if( data.password == null || data.passwordConfirm == null 
					|| data.password.replaceAll("\\s+", "").equals("") 
					|| data.passwordConfirm.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to delete account with invalid password or password confirmation.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password ou confirmação inválida.").build();
			}
			
			//Check if given password and confirmation are "correct" to the one on the database
			if( !DigestUtils.sha512Hex(data.password).equals(user.getString("password")) 
					|| !DigestUtils.sha512Hex(data.passwordConfirm).equals(user.getString("password")) ) {
				LOG.warning("Incorrect password or confirmation on account delete.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password ou confirmação incorretas.").build();
			}
			
			//Check if user is owner of institution or group. If so, account can't be deleted
			//This check is actually not necessary, because of RBAC, INST_OWNER would fail on entry
			String role = authToken.getString("role");
			if( role.equals(INST_OWNER) || role.equals(GROUP_OWNER) ) {
				LOG.warning("Attempt to delete account as role " + role);
				txn.rollback();
				if(role.equals(INST_OWNER))
					return Response.status(Status.FORBIDDEN).entity("Você é dono de uma instituição, logo não pode remover a sua conta.").build();
				else
					return Response.status(Status.FORBIDDEN).entity("Você é dono de um grupo, logo não pode remover a sua conta.").build();
			}
			
			//Check if user is in an ongoing event.
			if( !user.getString("inEvent").equals("") ) {
				LOG.warning(authToken.getString("username") + " attempt to delete account while joined in ongoing event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Você está a participar num evento, não pode eliminar.").build();
			}
			
			//Get user's event participations
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("UserEventsJoined")
					.setFilter(PropertyFilter.eq("username", username))
					.build();
			
			QueryResults<Entity> tasks = txn.run(query);
			
			tasks.forEachRemaining(participation -> {
				String eventId = participation.getString("eventId");
				Key eventKey = eventKeyFactory.newKey(eventId);
				Entity event = txn.get(eventKey);
					
				//Only remove future events, past are kept for history
				if( event.getLong("date_start") > System.currentTimeMillis() ) {
					//Lower the number of participants in event
					event = Entity.newBuilder(event)
							.set("joinedUsersCount", event.getLong("joinedUsersCount") - 1)
							.build();
					
					//Delete the participation
					txn.delete(participation.getKey());
					
					//Update the event
					cacheSupport.cache.put(event.getKey().getName(), event);
					txn.put(event);
				}
			});

			
			//Get joined groups and remove user from said groups
			/*
			List<Value<String>> groupsJoined = user.getList("groupsJoined");
			for( Value<String> v : groupsJoined ) {
				Key groupKey = groupKeyFactory.newKey(v.get().replaceAll("\\s+", "").toLowerCase());
				Entity group = txn.get(groupKey);
				String groupId = group.getKey().getName();
				List<Value<String>> newParticipants = removeStringFromListValuesString(group.getList("participants"), authToken.getString("username"));
				group = Entity.newBuilder(group)
						.set("participants", newParticipants)
						.build();
				txn.put(group);
				cacheSupport.cache.put(groupId, group);
			}
			*/
			
			//Get and Delete all active tokens of the user, and delete the user account afterwards
			Query<Entity> queryAuth = Query.newEntityQueryBuilder()
					.setKind("AuthToken")
					.setFilter(PropertyFilter.eq("username", username))
					.build();
			QueryResults<Entity> tasksAuth = txn.run(queryAuth);
			
			tasksAuth.forEachRemaining(autTok -> { 
				Key key = autTok.getKey();
				txn.delete(key);
				cacheSupport.cache.remove(key.getName());
			});

			//Delete photo first, if not default
			if( profile.getString("photo").contains(PROFILES) ){
				String oldFileName = PROFILES + profile.getString("photo").split(PROFILES)[1];
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, oldFileName);
			}
			
			//Delete user and profile from database and cache
			txn.delete(userKey, profileKey);
			cacheSupport.cache.remove(username);
			cacheSupport.cache.remove(username + "profile");
			
			//Add log of delete to database
			r = addLog(txn, "deleteUser", username, username);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			LOG.info("Account deleted successfully " + username);
			txn.commit();
			return Response.ok().entity("Conta removida com sucesso.").build();
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
	 * Method used to delete an Institution by it's owner. Owner reverts to role USER.
	 * Institution can be deleted if no ongoing events, and all future events are cascade deleted, and participants 
	 * are notified with a notification.
	 * If one of the institution's events starts in less than 24h, deletion is cancelled
	 * @param data - password, passwordConfirm, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@DELETE
	@Path("/institution")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deleteInstitution(InstitutionData data) {
		LOG.info("Attempt to delete institution.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "deleteInstitution");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			String username = authToken.getString("username");
			Key userKey = userKeyFactory.newKey(username.toLowerCase());
			Entity user = txn.get(userKey);
			
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
			else
				return Response.status(Status.NOT_FOUND).entity("Não é representante de nenhuma instituição.").build();
			
			//Check if given password and confirmation are valid
			if( data.password == null || data.password.replaceAll("\\s+", "").equals("") 
					|| data.passwordConfirm == null || data.passwordConfirm.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to delete institution with invalid password or confirmation.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password ou confirmação inválida.").build();
			}
			
			//Check if given password and confirmation are correct
			if( !DigestUtils.sha512Hex(data.password).equals(user.getString("password")) 
					|| !DigestUtils.sha512Hex(data.passwordConfirm).equals(user.getString("password")) ) {
				LOG.warning("Incorrect password or confirmation on institution remove.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password ou confirmação incorretas.").build();
			}

			String institutionName = institution.getKey().getName();
			
			//Get list of ongoing and future events
			query = Query.newEntityQueryBuilder()
					.setKind("Event")
					.setFilter(CompositeFilter.and(
							PropertyFilter.eq("institution", institutionName), 
							PropertyFilter.ge("date_end", System.currentTimeMillis())))
					.build();
			tasks = txn.run(query);
			
			List<Entity> ongoingEvents = new ArrayList<Entity>();
			List<Entity> futureEvents = new ArrayList<Entity>();
			
			//Separate ongoing and future events into separate lists
			tasks.forEachRemaining(e -> {
				if( e.getLong("date_start") <= System.currentTimeMillis() ) {
					ongoingEvents.add(e);
				}
				else
					futureEvents.add(e);
			});
			
			//If institution has ongoing events, can't delete it
			if( ongoingEvents.size() != 0 ) {
				LOG.warning("Attempt to delete institution with ongoing events.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Instituição tem eventos decorrentes, não pode ser removida.").build();
			}
			//If institution has future events, delete said events and it's markers, and notify participants
			//Events can only be deleted at most x prior to start date 
			else if( futureEvents.size() != 0 ) 
				for( Entity e : futureEvents ) {
					//Check if deletion attempted within x of start date, if so, can't delete
					Key eventMinStartKey = codeValueKeyFactory.newKey("mineventstart");
					long eventMinStart = datastore.get(eventMinStartKey).getLong("value") /(60*1000); 
					
					if( e.getLong("date_start") < System.currentTimeMillis() + eventMinStart ) {
						//We commit here, because we want to save previous event deletions, and added notifications
						LOG.warning("Attempt to delete institution with events sooner than 24h.");
						txn.commit();
						return Response.status(Status.FORBIDDEN)
								.entity("Remoção de instituição parada, visto que um dos seus eventos ("+ e.getString("name") +") não "
										+ "pode ser eliminado (menos de 24h até à data de início).")
								.build();
					}
					
					//Check if the event has any participants. If so, create notifications for those users
					//and remove their event participations
					query = Query.newEntityQueryBuilder()
							.setKind("UserEventsJoined")
							.setFilter(PropertyFilter.eq("eventId", e.getKey().getName()))
							.build(); 
					tasks = txn.run(query);
					
					tasks.forEachRemaining(participation -> {
						String usernameTemp = participation.getString("username");
						
						//Create notification
						Key notificationKey = notificationKeyFactory.newKey(usernameTemp + e.getKey().getName());
						Entity notification = Entity.newBuilder(notificationKey)
								.set("username", usernameTemp)
								.set("text", "Evento " + e.getString("name") + " em que estava inscrito foi eliminado.")
								.set("delivered", false)
								.build();
						txn.put(notification);
						
						//Delete participation
						txn.delete(participation.getKey());
					});

					//Delete event markers from database
					//Prepare query that will get markers
					EntityQuery.Builder queryMk = Query.newEntityQueryBuilder()
							.setKind("Marker")
							.setFilter(PropertyFilter.hasAncestor(e.getKey()));
				
					//Get markers
					QueryResults<Entity> tasksMk = txn.run(queryMk.build());

					//Delete markers
					while( tasksMk.hasNext() ) {
						Entity mrkr = tasksMk.next();
						txn.delete(mrkr.getKey());
					}
					
					//Delete comments and other uploaded photos related to this event
					//Get photos
					query = Query.newEntityQueryBuilder()
							.setKind("PhotoEventLog")
							.setFilter(PropertyFilter.eq("eventId", e.getKey().getName()))
							.build();
							
					tasks = txn.run(query);		
					
					//Delete all photos and logs
					while( tasks.hasNext() ) {
						Entity photo = tasks.next();
						
						String fileLink = photo.getString("photoLink");
						String fileName = EVENTS + fileLink.split(EVENTS)[1];
						
						Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
						storage.delete(BUCKET_ID, fileName);
						
						txn.delete(photo.getKey());
					}
					
					//Get comments
					query = Query.newEntityQueryBuilder()
							.setKind("Comment")
							.setFilter(PropertyFilter.hasAncestor(e.getKey()))
							.build();
							
					tasks = txn.run(query);		
					
					while( tasks.hasNext() ) {
						Entity comment = tasks.next();
						
						//Check if comment had photo, if so delete
						if( !comment.getString("photoLink").equals("") ) {	
							
							String fileName = comment.getString("photoId");
							
							Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
							storage.delete(BUCKET_ID, fileName);
						}
						
						txn.delete(comment.getKey());
					}
					
					//Remove event from database and cache
					txn.delete(e.getKey());					
					cacheSupport.cache.remove(e.getKey().getName());
				}
	
			//If role is SU, stays SU. Otherwise, lose INST_OWNER and revert to USER
			if( !user.getString("role").equals(SU) ) {
				
				//Given that we allow only one inst per user, no verifications needed, just change role
				user = Entity.newBuilder(user)
						.set("role", USER)
						.build();
				
				//Get user's auth tokens
				query = Query.newEntityQueryBuilder()
						.setKind("AuthToken")
						.setFilter(PropertyFilter.eq("username", username))
						.build();
				tasks = txn.run(query);
				
				//Change role on all of user's active tokens
				tasks.forEachRemaining(autTok -> { 	
					autTok = Entity.newBuilder(autTok)
							.set("role", USER)
							.build();
					txn.put(autTok);
					cacheSupport.cache.put(autTok.getKey().getName(), autTok);
				});
			}
			
			//Delete photo first, if not default
			if( institution.getString("photo").contains(INSTITUTIONS) ){
				String oldFileName = INSTITUTIONS + institution.getString("photo").split(INSTITUTIONS)[1];
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, oldFileName);
			}
			
			//Delete institution from database and update user
			txn.delete(institution.getKey());
			txn.put(user);
			
			cacheSupport.cache.remove(institution.getKey().getName());
			cacheSupport.cache.put(username, user);
			
			//Add log of delete to database
			r = addLog(txn, "deleteInstitution", username, institutionName);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			String role = "";
			if( user.getString("role").equals(SU) ) {
				role = SU;
			}
			else
				role = USER;
			
			//Create new token to send back to user for hiding/showing display purposes
			AuthToken at = new AuthToken(authToken.getKey().getName(), authToken.getString("username"), role, authToken.getLong("expirationDate"));

			LOG.info("Institution removed successfully " + institutionName);
			txn.commit();
			return Response.ok(g.toJson(at)).build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			e.printStackTrace();
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if(txn.isActive())
				txn.rollback();
		}
	}
	
	
	/**
	 * Method used by an inst owner to delete one of his events.
	 * Event mustn't be ongoing, and start date must be farther than 24h.
	 * Finished events can't be deleted.
	 * @param data - name, password, passwordConfirm, at
	 * @return response message with status
	 */
	@DELETE
	@Path("/event")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deleteEvent(EventData data) {
		LOG.info("Attempt to delete event.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "deleteEvent");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			String username = authToken.getString("username").toLowerCase();
			Key userKey = userKeyFactory.newKey(username);
			Entity user = txn.get(userKey);
			
			//Check if event name is valid
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Invalid event name on delete event.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventName);
			Entity event = txn.get(eventKey);
			
			//Check if event exists in database
			if( event == null ) {
				LOG.warning("Attempt to delete unexistent event.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			String instName = event.getString("institution").replaceAll("\\s+", "").toLowerCase();
			Key instKey = institutionKeyFactory.newKey(instName);
			Entity inst = txn.get(instKey);
			
			//Check if user attempting delete is the owner of the institution hosting the event
			if( !inst.getString("owner").toLowerCase().equals(username) ) {
				LOG.warning("Attempt to delete event by not owner of hosting institution.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Apenas o dono da instituição e do evento o pode remover.").build();
			}

			//Check if given password and confirmation are correct
			if( !DigestUtils.sha512Hex(data.password).equals(user.getString("password")) 
					|| !DigestUtils.sha512Hex(data.passwordConfirm).equals(user.getString("password")) ) {
				LOG.warning("Incorrect password or confirmation.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password ou confirmação incorretas.").build();
			}
			
			//Check if event is already finished. If so, can't delete
			if( event.getLong("date_end") < System.currentTimeMillis() ) {
				LOG.warning("Attempt to delete finished event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento já terminou, não pode ser removido.").build();
			}
			
			//Check if event is ongoing. If so, can't delete.
			if( event.getLong("date_start") < System.currentTimeMillis() && event.getLong("date_end") > System.currentTimeMillis()) {
				LOG.warning("Attempt to delete ongoing event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento está a decorrer, não pode ser removido.").build();
			}

			//Check if deletion attempted within x of start date, if so, can't delete
			Key eventMinStartKey = codeValueKeyFactory.newKey("mineventstart");
			long eventMinStart = txn.get(eventMinStartKey).getLong("value") /(60*1000); 

			if( event.getLong("date_start") < System.currentTimeMillis() + eventMinStart   ) {
				LOG.warning("Attempt to delete event within 24h date start.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN)
						.entity("Evento não pode ser eliminado. Eventos podem ser eliminados no máximo " + eventMinStart + " minutos antes do seu início.")
						.build();
			}
			
			String eventId = event.getKey().getName().toLowerCase();
			
			//Check if the event has any participants. If so, create notifications for those users
			//Get event participations
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("UserEventsJoined")
					.setFilter(PropertyFilter.eq("eventId", eventId))
					.build();
			
			QueryResults<Entity> tasks = txn.run(query);

			tasks.forEachRemaining(participation -> {
				String usernameTemp = participation.getString("username");
				
				Key notificationKey = notificationKeyFactory.newKey(usernameTemp + eventId);
				Entity notification = Entity.newBuilder(notificationKey)
						.set("username", usernameTemp)
						.set("text", "Evento " + event.getString("name") + " em que estava inscrito foi eliminado.")
						.set("delivered", false)
						.build();
				
				txn.delete(participation.getKey());
				txn.put(notification);
			});
		
			//Delete event markers from database
			//Prepare query that will get markers
			EntityQuery.Builder queryMrkr = Query.newEntityQueryBuilder()
					.setKind("Marker")
					.setFilter(PropertyFilter.eq("eventId", eventId));
		
			//Get markers
			QueryResults<Entity> tasksMrkr = txn.run(queryMrkr.build());

			//Delete markers
			while( tasksMrkr.hasNext() ) {
				Entity task = tasksMrkr.next();
				txn.delete(task.getKey());
			}

			//Delete photo first, if not default
			if( event.getString("photo").contains(EVENTS) ){
				String oldFileName = EVENTS + event.getString("photo").split(EVENTS)[1];
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, oldFileName);
			}
			
			//Delete comments and other uploaded photos related to this event
			//Get photos
			query = Query.newEntityQueryBuilder()
					.setKind("PhotoEventLog")
					.setFilter(PropertyFilter.eq("eventId", eventId))
					.build();
					
			tasks = txn.run(query);		
			
			//Delete all photos and logs
			while( tasks.hasNext() ) {
				Entity photo = tasks.next();
				
				String fileLink = photo.getString("photoLink");
				String fileName = EVENTS + fileLink.split(EVENTS)[1];
				
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, fileName);
				
				txn.delete(photo.getKey());
			}
			
			//Get comments
			query = Query.newEntityQueryBuilder()
					.setKind("Comment")
					.setFilter(PropertyFilter.hasAncestor(eventKey))
					.build();
					
			tasks = txn.run(query);		
			
			while( tasks.hasNext() ) {
				Entity comment = tasks.next();
				
				//Check if comment had photo, if so delete
				if( !comment.getString("photoLink").equals("") ) {	
					
					String fileName = comment.getString("photoId");
					
					Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
					storage.delete(BUCKET_ID, fileName);
				}
				
				txn.delete(comment.getKey());
			}
			
			//Delete event from database and cache
			txn.delete(eventKey);
			cacheSupport.cache.remove(eventKey.getName());

			//Add log of delete to database
			r = addLog(txn, "deleteEvent", username, eventName);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}

			LOG.info("Event deleted successfully " + eventName);
			txn.commit();
			return Response.ok().entity("Evento removido com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if(txn.isActive())
				txn.rollback();
		}
	}
	
	
	
	
	
	/**
	 * Method used by a user to delete his profile picture and revert to default picture
	 * @param data - at
	 * @return response message with status
	 *//*
	@DELETE
	@Path("/photoProfile")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deletePhotoProfile(UserData data) {
		LOG.info("Attempt to delete profile photo.");

		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			// Check login
			Response r = checkLogin(txn, data.at, authToken);
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "deletePhotoProfile");
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			String username = authToken.getString("username");
			Key profileKey = profileKeyFactory.addAncestors(PathElement.of("User", username)).newKey(username);
			Entity profile = txn.get(profileKey);

			//Delete photo from cloud bucket, if not default
			if( profile.getString("photo").contains(PROFILES) ){
				String fileName = PROFILES + profile.getString("photo").split(PROFILES)[1];
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, fileName);
			}

			//Edit photo link to default
			profile = Entity.newBuilder(profile)
					.set("photo", DEFAULT_USER_IMG)
					.build();

			txn.put(profile);
			cacheSupport.cache.put(username + "profile", profile);
			
			txn.commit();

			LOG.info("Profile photo deleted successfully");
			return Response.ok().entity("Foto de perfil removida com sucesso.").build();

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
	 * Method used to delete event photo and revert to default image.
	 * Can be used by inst owner to delete own event photo, or BO to delete other photos
	 * @param data - name, at
	 * @return response message with status
	 *//*
	@SuppressWarnings("unchecked")
	@DELETE
	@Path("/photoEvent")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deletePhotoEvent(EventData data) {
		LOG.info("Attempt to delete event photo.");

		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			// Check login
			Response r = checkLogin(txn, data.at, authToken);
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "deletePhotoEvent");
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check if event name is valid
			if (data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to delete event photo with invalid event name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventName);
			Entity event = txn.get(eventKey);

			// Check if event exists in database
			if (event == null) {
				LOG.warning("Attempt to delete event photo of unexistent event.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			String username = authToken.getString("username").toLowerCase();
			String instName = event.getString("institution").replaceAll("\\s+", "").toLowerCase();
			Key instKey = institutionKeyFactory.newKey(instName);
			Entity inst = txn.get(instKey);

			//Check if user attempting delete is the owner of the institution hosting the event
			if( !inst.getString("owner").toLowerCase().equals(username) ) {
				LOG.warning("Attempt to delete event photo by not the owner.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Apenas o dono da instituição e do evento pode remover a foto do evento.").build();
			}

			//Check if event is already finished. If so, can't delete photo
			if (event.getLong("date_end") < System.currentTimeMillis()) {
				LOG.warning("Attempt to delete photo of finished event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento já terminou, não pode remover a foto deste.").build();
			}

			// Check if event is ongoing. If so, can't delete.
			if (event.getLong("date_start") < System.currentTimeMillis() && event.getLong("date_end") > System.currentTimeMillis()) {
				LOG.warning("Attempt to delete photo on ongoing event.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Evento está a decorrer, não pode remover a foto deste.").build();
			}
			
			//Delete photo from bucket, if not default
			if( event.getString("photo").contains(EVENTS) ){
				String fileName = EVENTS + event.getString("photo").split(EVENTS)[1];
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, fileName);
			}
			
			//Update event photo
			event = Entity.newBuilder(event)
					.set("photo", DEFAULT_EVENT_IMG)
					.build();

			txn.put(event);
			cacheSupport.cache.put(eventKey.getName(), event);
			
			txn.commit();
			
			LOG.info("Event photo deleted successfully.");
			return Response.ok().entity("Foto do evento removida com sucesso.").build();

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
	 * Method used to delete an image uploaded to a marker.
	 * Can delete own images
	 * @param data - photoLink, at
	 * @return response message with status
	 *//*
	@DELETE
	@Path("/photoEventMarker")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deletePhotoEventMarker(EventData data) {
		LOG.info("Attempt to delete event marker photo.");

		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			// Check login
			Response r = checkLogin(txn, data.at, authToken);
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "deletePhotoEventMarker");
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check if event name is valid
			if (data.name == null || data.name.equals("") ) {
				LOG.warning("Attempt to delete event marker photo with invalid event name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventName);
			Entity event = txn.get(eventKey);

			// Check if event exists in database
			if (event == null) {
				LOG.warning("Attempt to delete event marker photo of unexistent event.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			String username = authToken.getString("username").toLowerCase();
			Key photoMrkrLogKey = photoMarkerLogKeyFactory.newKey(data.name);
			Entity photoMrkrLog = txn.get(photoMrkrLogKey);
			
			//If role is USER, check if photo was uploaded by user
			if( authToken.getString("role").equals(USER) && !photoMrkrLog.getString("owner").equals(username) ) {
				txn.rollback();
				LOG.warning("Attempt to delete not own photo on delete photo marker.");
				return Response.status(Status.FORBIDDEN).entity("Não tem permissão para eliminar esta foto.").build();
			}
			
			//Delete photo from bucket
			String fileName = EVENTS + data.photoLink.split(EVENTS)[1];
			Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
			storage.delete(BUCKET_ID, fileName);

			//Update event list of photos
			event = Entity.newBuilder(event)
					.set("photos", removeStringFromListValuesString(event.getList("photos"), data.photoLink))
					.build();

			txn.delete(photoMrkrLogKey);
			txn.put(event);		
			txn.commit();
			
			LOG.info("Event photo deleted successfully.");
			return Response.ok().entity("Foto removida com sucesso.").build();

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
	 * Method used to delete institution photo
	 * Can be used by the owners to delete own inst photo, or BO to delete other inst photo
	 * @param data - name, at
	 * @return
	 *//*
	@DELETE
	@Path("/photoInstitution")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deletePhotoInstitution(InstitutionData data) {
		LOG.info("Attempt to delete institution photo.");

		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			// Check login
			Response r = checkLogin(txn, data.at, authToken);
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "deletePhotoInstitution");
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			Entity inst = null;
			
			//Get institution. If INST_OWNER get own inst, if BO get given inst
			if( authToken.getString("role").equals(INST_OWNER) ) {
				//Get user's institution
				Query<Entity> query = Query.newEntityQueryBuilder()
						.setKind("Institution")
						.setFilter(PropertyFilter.eq("owner", authToken.getString("username")))
						.build(); 
				
				QueryResults<Entity> tasks = datastore.run(query);
				
				//Check if institution exists in database
				if( tasks.hasNext() )
					inst = tasks.next();
				else
					return Response.status(Status.NOT_FOUND).entity("Não é representante de nenhuma instituição.").build();
			}
			else {
				String instName = data.name.replaceAll("\\s+", "").toLowerCase();
				Key instKey = institutionKeyFactory.newKey(instName);
				inst = txn.get(instKey);
			}

			//Check if institution exists in database
			if (inst == null) {
				LOG.warning("Attemp to delete photo on unexistent instituition,");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Instituição não existe.").build();
			}

			//Delete photo from cloud storage
			if( inst.getString("photo").contains(INSTITUTIONS) ){
				String fileName = INSTITUTIONS + inst.getString("photo").split(INSTITUTIONS)[1];
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, fileName);
			}
			
			//Update the photo
			inst = Entity.newBuilder(inst)
					.set("photo", DEFAULT_INST_IMG)
					.build();

			txn.put(inst);
			cacheSupport.cache.put(inst.getKey().getName(), inst);
			
			txn.commit();
			
			LOG.info("Institution photo delete successful");
			return Response.ok().entity("Foto da instituição removida com sucesso.").build();

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
	 * Method used by a user to remove an event from his save/favorited events list
	 * @param data - name, at
	 * @return response message with status
	 */
	@DELETE
	@Path("/savedEvent")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deleteSavedEvent(EventData data) {
		LOG.info("Attempt to remove saved event.");

		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			// Check login
			Response r = checkLogin(txn, data.at, authToken);
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "deleteSavedEvent");
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			//Check if given event name is valid
			if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to remove saved event with invalid name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
			}
			
			String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
			
			//Check if event exists in database
			Key eventKey = eventKeyFactory.newKey(eventId);
			Entity event = txn.get(eventKey);
			
			if( event == null ) {
				LOG.warning("Attempt to save null event.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
			}
			
			String username = authToken.getString("username");

			//Check user saved the given event
			Key savedEventKey = savedEventKeyFactory.newKey(eventId + username);
			Entity savedEvent = txn.get(savedEventKey);
			
			if( savedEvent == null ) {
				LOG.warning("Attempt to delete saved event with not saved event.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Evento indicado não está na sua lista de favoritos.").build();
			}
			
			txn.delete(savedEventKey);
			txn.commit();
			
			LOG.info("Event removed from saved successfully.");
			return Response.ok().entity("Evento removido com sucesso.").build();

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
	 * Method used to delete an event comment.
	 * Users can delete their own past comments, or BO and SU users can remove other comments, for moderation
	 * @param data - commentId, at
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	@DELETE
	@Path("/eventComment")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deleteComment(CommentData data) {
		LOG.info("Attempt to delete event comment.");

		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			// Check login
			Response r = checkLogin(txn, data.at, authToken);
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "deleteComment");
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			//Check if given data is valid
			if( data.eventName == null || data.commentId == null 
					|| data.eventName.replaceAll("\\s+", "").equals("") || data.commentId.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Invalid data on delete event comment.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do evento ou ID do comentário inválido.").build();
			}
			
			String eventName = data.eventName.replaceAll("\\s+", "").toLowerCase();
			String username = authToken.getString("username");
			
			Key userKey = userKeyFactory.newKey(username);
			Entity user = txn.get(userKey);

			String commentId = data.commentId;
			Key commentKey = commentKeyFactory.addAncestor(PathElement.of("Event", eventName)).newKey(commentId);

			Entity comment = txn.get(commentKey);
			
			if( comment == null ) {
				LOG.warning("Attempt to delete unexistent comment.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Comentário não existe.").build();
			}
			
			//Check deletion rules
			//User can delete own comments, or BO and SU can delete any comment
			if( comment.getString("owner").equals(authToken.getString("username")) 
					|| user.getString("role").equals(BO) || user.getString("role").equals(SU) ) {
				
				//Check if comment has photo associated. If so, delete photo as well, and remove from list of event photos
				if( !comment.getString("photoLink").equals("") ) {					
					Key eventKey = eventKeyFactory.newKey(eventName);
					Entity event = txn.get(eventKey);
					
					//Delete from storage
					Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
					storage.delete(BUCKET_ID, comment.getString("photoId"));
					
					//Delete from event
					event = Entity.newBuilder(event)
							.set("photos", removeStringFromListValuesString(event.getList("photos"), comment.getString("photoLink")))
							.build();
					
					txn.put(event);
					cacheSupport.cache.put(eventKey.getName(), event);
				}
				
				//Delete comment
				txn.delete(commentKey);				
				txn.commit();
			}
			else {
				LOG.warning("Failure on attempt to delte comment, either not owner of comment, or not BO/SU.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Não tem permissão para eliminar esse comentário.").build();
			}

			LOG.info("Event comment deleted successfully.");
			return Response.ok().entity("Comentário removido com sucesso.").build();

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
	 * Method used by a BO or SU user to remove an item from the shop
	 * @param data - name, at
	 * @return response message with status
	 */
	@DELETE
	@Path("/shopItem")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deleteShopItem(ShopItemData data) {
		LOG.info("Attempt to remove shop item.");

		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			// Check login
			Response r = checkLogin(txn, data.at, authToken);
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "deleteShopItem");
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			//Check validity of given item name
			if( data.itemName == null || data.itemName.replaceAll("\\s+", "").equals("") ) {
				LOG.warning("Attempt to delete shop item with invalid name.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do item inválido").build();
			}
			
			String shopItemName = data.itemName.replaceAll("\\s+", "").toLowerCase();
			Key shopItemKey = shopItemKeyFactory.newKey(shopItemName);
			Entity shopItem = txn.get(shopItemKey);
			
			//Check if item exists in database
			if( shopItem == null ) {
				LOG.warning("Attempt to remove unexistent shop item.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Item não existe.").build();
			}
			
			//Delete photo, if not default
			if( shopItem.getString("photo").contains(SHOP_ITEMS) ){
				String oldFileName = SHOP_ITEMS + shopItem.getString("photo").split(SHOP_ITEMS)[1];
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, oldFileName);
			}
			
			//Add log of delete to database
			r = addLog(txn, "deleteEvent", authToken.getString("username"), shopItem.getKey().getName());
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			//Remove item from shop
			txn.delete(shopItemKey);
			
			txn.commit();
			
			LOG.info("Shop item removed from successfully.");
			return Response.ok().entity("Item removido com sucesso.").build();

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
	 * Method used to delete a group.
	 * @param data - name, password, passwordConfirm, at
	 * @return response message with status
	 *//*
	@DELETE
	@Path("/group")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deleteGroup(GroupData data) {
		LOG.info("Attempt to delete group.");
		
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
			r = checkRBAC(txn, authToken.getString("role"), "deleteGroup");
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			Key userKey = userKeyFactory.newKey(authToken.getString("username"));
			Entity user = txn.get(userKey);
			String username = authToken.getString("username");
						
			//Check if group name is valid
			if( data.name == null || data.name.equals("") ) {
				LOG.warning("Attempt to delete group with invalid group name");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome do grupo inválido.").build();
			}
			
			//Check if given password and confirmation are correct
			if( !DigestUtils.sha512Hex(data.password).equals(user.getString("password")) 
					|| !DigestUtils.sha512Hex(data.passwordConfirm).equals(user.getString("password")) ) {
				LOG.warning("Incorrect password or confirmation.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Password ou confirmação incorretas.").build();
			}
			
			String groupName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key groupKey = eventKeyFactory.newKey(groupName);
			Entity group = txn.get(groupKey);
			
			//Check if group exists in database
			if( group == null ) {
				LOG.warning("Attempt to delete unexistent group.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Grupo não existe.").build();
			}

			//Check if user attempting delete is the owner of the group
			if( !group.getString("owner").toLowerCase().equals(username) ) {
				LOG.warning("Attempt to delete group by not owner.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Apenas o dono do grupo o pode remover.").build();
			}

			//Check if the group has any participants. If so, can't delete
			if( !group.getList("participants").isEmpty() ) {
				LOG.warning("Attempt to delte group with participants.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Grupo com membros, não pode ser removido.").build();
			}
			
			//Check if group has joined events
			if( group.getList("eventsJoined").size() > 0 ) {
				LOG.warning("Attempt to delete group with joined events.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Grupo está inscrito em eventos, não pode ser removido.").build();
			}
			
			//Check if the group has any participants. If so, create notifications for those users
			if( !group.getList("participants").isEmpty() ) {
				List<Value<String>> users = group.getList("participants");
				for( Value<String> u : users ) {
					String usernameTemp = u.get().toLowerCase();
					
					//Build notification and add to database
					Key notificationKey = notificationKeyFactory.newKey(usernameTemp + group.getKey().getName());
					Entity notification = Entity.newBuilder(notificationKey)
							.set("username", usernameTemp)
							.set("text", "Grupo " + group.getString("name") + " em que estava inscrito foi eliminado.")
							.set("delivered", false)
							.build();
					txn.put(notification);
					
					//Edit user's list of participating groups
					Key userKeyTemp = userKeyFactory.newKey(usernameTemp);
					Entity userTemp = txn.get(userKeyTemp);
					userTemp = Entity.newBuilder(userTemp)
							.set("groupsJoined", removeStringFromListValuesString(userTemp.getList("groupsJoined"), group.getKey().getName()))
							.build();
					txn.put(userTemp);
					cacheSupport.cache.put(userTemp.getKey().getName(), userTemp);
				}
			}
			
			//If role is SU, stays SU. Otherwise, revert to USER
			if( !user.getString("role").equals(SU) ) {
				user = Entity.newBuilder(user)
						.set("role", USER)
						.build();
			
				//Get user's auth tokens
				EntityQuery query = Query.newEntityQueryBuilder()
						.setKind("AuthToken")
						.setFilter(PropertyFilter.eq("username", username))
						.build();
				QueryResults<Entity> tasks = txn.run(query);
				
				//Change role on all of user's active tokens
				tasks.forEachRemaining(autTok -> { 	
					autTok = Entity.newBuilder(autTok.getKey())
							.set("role", USER)
							.build();
					txn.put(autTok);
					cacheSupport.cache.put(autTok.getKey().getName(), autTok);
				});
			}
			
			//If institution had profile photo, delete from cloud storage
			if( !group.getString("photo").equals("") ) {
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, GROUP + authToken.getString("username"));
			}
				
			//Delete group from database and update user
			txn.put(user);
			txn.delete(groupKey);
			
			cacheSupport.cache.put(userKey.getName(), user);
			cacheSupport.cache.remove(groupKey.getName());
			
			//Add log of delete to database
			r = addLog(txn, "deleteGroup", username, groupName);
			if( r.getStatus() != 200 ) {
				txn.rollback();
				return r;
			}
			
			txn.commit();
			return Response.ok().entity("Grupo removido com sucesso.").build();
			
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Something broke.").build();
		} finally {
			if(txn.isActive())
				txn.rollback();
		}
	}*/
	
	
	/**
	 * Method used to delete photo of a group and rever to default image
	 * @param data - name, at
	 * @return response message with status
	 *//*
	@DELETE
	@Path("/photoGroup")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response deletePhotoGroup(GroupData data) {
		LOG.info("Attempt to delete group photo.");

		Transaction txn = datastore.newTransaction();
		try {
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);

			// Check login
			Response r = checkLogin(txn, data.at, authToken);
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check RBAC
			r = checkRBAC(txn, authToken.getString("role"), "deletePhotoGroup");
			if (r.getStatus() != 200) {
				txn.rollback();
				return r;
			}

			// Check if group name is valid
			if (data.name == null || data.name.equals("") ) {
				LOG.warning("Invalid group name on delte group photo.");
				txn.rollback();
				return Response.status(Status.BAD_REQUEST).entity("Nome de grupo inválido.").build();
			}

			String groupName = data.name.replaceAll("\\s+", "").toLowerCase();
			Key groupKey = groupKeyFactory.newKey(groupName);
			Entity group = txn.get(groupKey);

			// Check if group exists in database
			if (group == null) {
				LOG.warning("Attempt to delte photo of unexistent group.");
				txn.rollback();
				return Response.status(Status.NOT_FOUND).entity("Grupo não existe.").build();
			}

			String username = authToken.getString("username");
			
			//If role GROUP_OWNER, check if user attempting delete the photo is the owner of the institution
			if( authToken.getString("role").equals(GROUP_OWNER) && !group.getString("owner").equals(username)) {
				LOG.warning("Attempt to delete group photo by not the owner.");
				txn.rollback();
				return Response.status(Status.FORBIDDEN).entity("Apenas o dono do grupo pode remover a foto deste.").build();
			}
			
			//Delete photo from cloud storage
			if( group.getString("photo").contains(GROUPS) ){
				String fileName = GROUPS + group.getString("photo").split(GROUPS)[1];
				Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
				storage.delete(BUCKET_ID, fileName);
			}
		
			//Update photo link
			group = Entity.newBuilder(group)
					.set("photo", DEFAULT_GROUP_IMG)
					.build();

			txn.put(group);
			cacheSupport.cache.put(groupKey.getName(), group);
			
			txn.commit();
			
			LOG.info("Delete group photo successful");
			return Response.ok().entity("Foto de grupo removida com sucesso.").build();

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
	 * Method used to delete expired authentication tokens.
	 * It is called periodically by the cloud operations
	 */
	@DELETE
	@Path("/tokens")
	public void deleteExpiredTokens() {
		
		Query<Entity> query = Query.newEntityQueryBuilder()
				.setKind("AuthToken")
				.setFilter(PropertyFilter.le("expirationDate", System.currentTimeMillis()))
				.build();
		QueryResults<Entity> tasks = datastore.run(query);
		
		tasks.forEachRemaining(at -> {
			datastore.delete(at.getKey());
			cacheSupport.cache.remove(at.getKey().getName());
		});
	}
	
	
	/**
	 * Method used to add logs of deletion to the database
	 * @param txn - active transaction from where the method was called
	 * @param methodName - name of the method where the deletion happened
	 * @param username - username of the user invoking the deletion
	 * @param name - name of the entity being deleted
	 * @return response message with status
	 */
	private Response addLog(Transaction txn, String methodName, String username, String name) {
		LOG.info("Adding action to database log.");

		try {
			Key logKey = logKeyFactory.newKey(System.currentTimeMillis());
			Entity log = txn.get(logKey);
			
			log = Entity.newBuilder(logKey)
					.set("method", methodName.toLowerCase())
					.set("username", username)
					.set("name", name)
					.set("time", System.currentTimeMillis())
					.build();
			txn.add(log);
			LOG.fine("Action added to databse log.");
			return Response.ok().build();
		} catch( Exception e ) {
			txn.rollback();
			LOG.severe(e.getMessage());
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
	
	
	/*
	 * Used to check if a user is in a session, by doing some checks to authToken
	 */
	@SuppressWarnings("unchecked")
	private Response checkLogin(Transaction txn, AuthToken at, Entity authToken) {
		//Check both given token and database token
		if( at == null || authToken == null ) {
			LOG.warning("Attempt to operate with no login.");
			txn.rollback();
			return Response.status(Status.UNAUTHORIZED).entity("Login inexistente.").build();
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
			return Response.status(Status.FORBIDDEN).entity("Você não tem permissão para realizar esta ação.").build();
		} 
		else
			return Response.ok().build();
	}
	
	
	private List<Value<String>> removeStringFromListValuesString(List<Value<String>> list, String toRem) {
		List<Value<String>> listNew = new ArrayList<Value<String>>();
		
		for( Value<String> v : list ) 
			listNew.add(v);

		listNew.remove(StringValue.of(toRem));
		
		return listNew;
	}
	
}
