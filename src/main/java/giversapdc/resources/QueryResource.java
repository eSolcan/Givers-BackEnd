package giversapdc.resources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.cloud.datastore.BooleanValue;
import com.google.cloud.datastore.Cursor;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.EntityQuery;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.LongValue;
import com.google.cloud.datastore.PathElement;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.Transaction;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.datastore.Value;
import com.google.gson.Gson;

import giversapdc.util.AuthToken;
import giversapdc.util.CacheSupport;
import giversapdc.util.MapMarker;
import giversapdc.util.Pair;
import giversapdc.util.QueryData;

@Path("/query")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class QueryResource {

	private static final Integer PAGE_SIZE = 5;
	private static final Integer TOPS_SIZE = 5;
	
	private static final String SUGGESTION = "Suggestion";
	private static final String REPORT = "Report";
	private static final String BUG = "Bug";
	
	private static final Logger LOG = Logger.getLogger(QueryResource.class.getName());
	private final Gson g = new Gson();

	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");
	private KeyFactory profileKeyFactory = datastore.newKeyFactory().setKind("Profile");
	private KeyFactory authTokenKeyFactory = datastore.newKeyFactory().setKind("AuthToken");
	private KeyFactory institutionKeyFactory = datastore.newKeyFactory().setKind("Institution");
	private KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind("Event");
	//private KeyFactory groupKeyFactory = datastore.newKeyFactory().setKind("Group");
	private KeyFactory rbacKeyFactory = datastore.newKeyFactory().setKind("AccessControl");
	private KeyFactory markerKeyFactory = datastore.newKeyFactory().setKind("Marker");
	private KeyFactory userEventsJoinedKeyFactory = datastore.newKeyFactory().setKind("UserEventsJoined");
	private KeyFactory savedEventKeyFactory = datastore.newKeyFactory().setKind("SavedEvent");
	
	private CacheSupport cacheSupport = new CacheSupport();

	public QueryResource() { }

	/**
	 * Method used to retrieve the profile of a user
	 * @param data - at
	 * @return json with profile properties
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/profile")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getProfile(QueryData data) {		

		Transaction txn = datastore.newTransaction();
		try {
		
			//Get authToken, either from cache, or database
			Entity authToken = getAuthToken(data.at.tokenID);
			
			//Check login
			Response r = checkLogin( data.at, authToken);
			if( r.getStatus() != 200 ) {
				return r;
			}
			
			//Check RBAC
			r = checkRBAC(authToken.getString("role"), "getProfile");
			if( r.getStatus() != 200 ) {
				return r;
			}
			
			Entity user = (Entity) cacheSupport.cache.get(authToken.getString("username"));
			Entity profile = (Entity) cacheSupport.cache.get(authToken.getString("username") + "profile");
			
			//Get user from database, if wasn't present in cache
			if( user == null ) {
				Key userKey = userKeyFactory.newKey(authToken.getString("username"));
				user = txn.get(userKey);
			}
	
			//Check if user is null
			if( user == null ) {
				LOG.warning("Attemppt to get profile with null user.");
				return Response.status(Status.NOT_FOUND).entity("Utilizador não existe.").build();
			}
			else
				cacheSupport.cache.put(user.getString("username"), user);
			
			//Get profile from database, if wasn't present in cache
			if( profile == null ) {
				Key profileKey = profileKeyFactory
						.addAncestor(PathElement.of("User", authToken.getString("username").toLowerCase()))
						.newKey(authToken.getString("username"));
				profile = txn.get(profileKey);
			}		
			
			//Check if profile is not null
			if( profile == null ) {
				LOG.warning("Attempt to query for null profile/user.");
				return Response.status(Status.NOT_FOUND).entity("Utilizador não existe.").build();
			}
			else
				cacheSupport.cache.put(user.getKey().getName() + "profile", profile);
				
			/*	Regarding sending properties to the frontend, this applies to all instances where we do this
			 *	(unless sending all properties).
			 *
			 * 	This is seems to be quite inefficient, but also seems to be along the best options, because of two reasons:
			 *	
			 * 	1 - Sending the whole list of properties is a bad idea, because we don't want to 
			 *		make certain properties public, such ass passwords and emails of the users.
			 *	2 - In following of topic 1, maps from the datastore are imutable, so we can't simply 
			 *		remove the properties that we don't want to send to the frontend without first 
			 *		copying each element into a new map
			 *	
			 *	The two solutions that we thought of involve either adding each property that we want to send one by one, 
			 *	or copying the whole map, and removing the properties that we don't want to send.
			 *	Both will be used in different circumstances, although the "one by one" might be slightly more efficient.
			 */
	
			//Get user profile properties
			Map<String, Value<?>> props = new HashMap<String, Value<?>>();
			props.putAll(profile.getProperties());
			props.putAll(user.getProperties());
			
			//Remove and add certain properties
			props.remove("password");
			props.remove("state");
			props.remove("creation_time");
			
			//Get events within a specific range of latitude
			Query<Entity> query = Query.newEntityQueryBuilder()
					.setKind("UserEventsJoined")
					.setFilter(CompositeFilter.and(PropertyFilter.eq("username", authToken.getString("username")), PropertyFilter.eq("participated", true)))
					.build();
					
			QueryResults<Entity> tasks = txn.run(query);
			
			long eventCount = 0;

			while( tasks.hasNext() ) {
				tasks.next();
				eventCount++;
			}
				
			props.put("eventCount", LongValue.of(eventCount));
			
			return Response.ok(g.toJson(props)).build();
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
	 * Method used to retrieve a specified event
	 * @param data - name, at
	 * @return json with event properties
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/event")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEvent(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getEvent");
		if( r.getStatus() != 200 ) {
			return r;
		}

		//Check event name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get event with invalid event name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome inválido.").build();
		}
		
		String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
		Entity event = (Entity) cacheSupport.cache.get(eventId);
		
		//Get event from database if not present in cache
		if( event == null ) {
			Key eventKey = eventKeyFactory.newKey(eventId);
			event = datastore.get(eventKey);
			
		}
		
		//Check if event exists in database
		if( event == null ) {
			LOG.warning("Attempt to get event with null event.");
			return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
		}
		else
			cacheSupport.cache.put(event.getKey().getName(), event);
		
		//Get event properties
		Map<String, Value<?>> props = new HashMap<String, Value<?>>();
		props.putAll(event.getProperties());
		
		//Remove certain properties
		props.remove("start_code");
		
		String username = authToken.getString("username");
		
		//Add if user is in event, used only in one situation on the web for a specific button
		Value<?> joined = BooleanValue.of(false);
		
		Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + username);
		Entity participation = datastore.get(participationKey);
		
		if( participation != null )
			joined = BooleanValue.of(true);
		
		props.put("inEvent", joined);
		
		//Add if user saved this event
		Value<?> saved = BooleanValue.of(false);
		
		Key savedEventKey = savedEventKeyFactory.newKey(eventId + username);
		Entity savedEvent = datastore.get(savedEventKey);
		
		if( savedEvent != null )
			saved = BooleanValue.of(true);
		
		props.put("saved", saved);
		
		return Response.ok(g.toJson(props)).build();
	} 
	
	
	/**
	 * Method used to retrieve a list of events.
	 * A cursor is used to send a specific number of items each time.
	 * Events can be filtered by past, future, all, and interest
	 * @param data - queryTime, interests, startCursorString, at
	 * @return json with a list of events, cursor
	 */
	@POST
	@Path("/eventsFiltered")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEventsFiltered(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getEventsFiltered");
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		Cursor startCursor = null;
		
		if( data.startCursorString != null && !data.startCursorString.equals("") ) {
			//Where query stopped last time
			startCursor = Cursor.fromUrlSafe(data.startCursorString);
		}
		
		List<Map<String, Value<?>>> events = new ArrayList<Map<String, Value<?>> >();
		
		//Prepare query that will get events
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("Event")
				.setOrderBy(OrderBy.asc("date_start"))
				.setLimit(PAGE_SIZE)
				.setStartCursor(startCursor);
		

		/*	-1 past, 0 all, 1 future (missing a "2 ongoing" here, but it's quite problematic, since we can't do inequality filters on different property)
			Per documentation: "(...) a single query may not use inequality comparisons on more than one property across all of its filters."
			So, given this we can't compare start and end date of an event simultaneously in a query.
		*/		
		PropertyFilter filter = null;
		if( data.queryTime == -1 ) {
			filter = PropertyFilter.lt("date_end", System.currentTimeMillis());
			query = query.setOrderBy(OrderBy.desc("date_end"));
		}
		else if ( data.queryTime == 0 )
			filter = null;
		else if ( data.queryTime == 1 )
			filter = PropertyFilter.gt("date_start", System.currentTimeMillis());	
			
		//Check if interest is null
		if( data.interests ==null  ) {
			LOG.warning("Attempt to get filtered events with null interest.");
			return Response.status(Status.BAD_REQUEST).entity("Valor do interesse não pode ser null.").build();
		}
		
		//If user gave interest, filter by that as well
		if( !data.interests.equals("") ) {
			if( !data.validInterest() ) {
				LOG.warning("Attempt to get filtered events.");
				return Response.status(Status.BAD_REQUEST).entity("Interesse inválido, selecione um da lista indicada.").build();
			}
			String interest = data.interests.replaceAll("\\s+", "").toLowerCase();
			if( filter != null )
				query.setFilter(CompositeFilter.and(PropertyFilter.eq("interests", interest), filter));
			else
				query.setFilter(PropertyFilter.eq("interests", interest));
		} 
		else
			query.setFilter(filter);
			
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		//Build maps with properties, adding all first then removing some
		tasks.forEachRemaining(event -> {
			
			//Get event properties
			Map<String, Value<?>> props = new HashMap<String, Value<?>>();
			props.putAll(event.getProperties());
			
			//Remove certain properties
			props.remove("start_code");
			
			//Add if user is in event, used only in one situation on the web for a specific button
			Value<?> joined = BooleanValue.of(false);
			
			Key participationKey = userEventsJoinedKeyFactory.newKey(event.getKey().getName() + authToken.getString("username"));
			Entity participation = datastore.get(participationKey);
			
			if( participation != null )
				joined = BooleanValue.of(true);
			
			props.put("inEvent", joined);
			
			//Add if user saved this event
			Value<?> saved = BooleanValue.of(false);
			
			Key savedEventKey = savedEventKeyFactory.newKey(event.getKey().getName() + authToken.getString("username"));
			Entity savedEvent = datastore.get(savedEventKey);
			
			if( savedEvent != null )
				saved = BooleanValue.of(true);
			
			props.put("saved", saved);
			
			events.add(props);
		});
		
		//Where to start next time
		Cursor cursor = tasks.getCursorAfter();
		if( cursor != null && events.size() == PAGE_SIZE ) {
			String cursorString = cursor.toUrlSafe();
			Pair p = new Pair(events, cursorString);
			return Response.ok(g.toJson(p)).build();
		}
		else {
			Pair p = new Pair(events, "end");
			return Response.ok(g.toJson(p)).build();
		}		
	} 

	
	/**
	 * Method used to retrieve a list of map markers of all events within around 50km around the given lat & lng
	 * Used an adaptation of one of the tips professor Carlos gave, as an alternative to geohashing.
	 * We run a single query that gets the markers within an exact range of latitude, and then filter those after for the latitude.
	 * It's not quite the correct "grid" way of doing it, but it's still more efficient than doing multiple queries or searching all markers.
	 * 
	 * Also, the api call name and method name are incorrect, since we actually get both ongoing and future events.
	 * @param data - lat, lng, at
	 * @return json with a list of markers
	 */
	@POST
	@Path("/eventsFutureMap")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEventsFutureMap(QueryData data) {
		
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getEventsFutureMap");
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Setup min and max lat lng with a range of around 50km around the center
		double minLat = data.lat - 0.5;
		double maxLat = data.lat + 0.5;
		double minLng = data.lng - 0.5;
		double maxLng = data.lng + 0.5;
				
		//double minLat = -90;
		//double maxLat = 90;
		//double minLng = -180;
		//double maxLng = 180;
		
		//Get events within a specific range of latitude
		Query<Entity> query = Query.newEntityQueryBuilder()
				.setKind("Event")
				.setFilter(CompositeFilter.and(PropertyFilter.ge("lat", minLat), PropertyFilter.le("lat", maxLat)))
				.build();
				
		QueryResults<Entity> tasks = datastore.run(query);
		
		//Add events to list. 
		List<Entity> events = new ArrayList<Entity>();
		tasks.forEachRemaining(event -> {
			//Filter out past events, and filter by the longitude of the bounding box
			if( ( event.getLong("date_end") > System.currentTimeMillis() && event.getLong("date_start") < System.currentTimeMillis() 
					|| event.getLong("date_start") > System.currentTimeMillis())
					&& event.getDouble("lon") >= minLng && event.getDouble("lon") <= maxLng)
				
				events.add(event);
		});

		//Get markers of all future events and add to list of events
		List<MapMarker> eventsMarkers = new ArrayList<MapMarker>();
		for( Entity ev : events ) {
			String eventId = ev.getString("name").replaceAll("\\s+", "").toLowerCase();
			MapMarker mrkr = new MapMarker(ev.getDouble("lat"), ev.getDouble("lon"), eventId, ev.getString("name"), ev.getString("description"));
			eventsMarkers.add(mrkr);
		}
		
		return Response.ok().entity(g.toJson(eventsMarkers)).build();
	}
	
	
	/**
	 * Method used to retrieve the map markers of a route of a specified event
	 * @param data - name, at
	 * @return json with a list of map markers
	 */
	@POST
	@Path("/eventRouteMarkers")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEventRouteMarkers(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getEventRouteMarkers");
		if( r.getStatus() != 200 ) {
			return r;
		}

		//Check event name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get event route markers with invalid event name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome inválido.").build();
		}
		
		Key eventKey = eventKeyFactory.newKey(data.name.replaceAll("\\s+", "").toLowerCase());
		
		//Get all event route markers
		Query<Entity> query = Query.newEntityQueryBuilder()
				.setKind("Marker")
				.setFilter(PropertyFilter.hasAncestor(eventKey))
				.build();
		
		QueryResults<Entity> tasks = datastore.run(query);
				
		//Get markers of all future events and add to list of events
		List<MapMarker> eventsMarkers = new ArrayList<MapMarker>();
		tasks.forEachRemaining(evMarker -> {
			//Build the marker
			MapMarker mrkr = new MapMarker(evMarker.getKey().getName(), evMarker.getDouble("lat"), evMarker.getDouble("lon"), 
					evMarker.getString("eventId"), evMarker.getString("eventName"), evMarker.getString("description"), evMarker.getBoolean("risk"));
			eventsMarkers.add(mrkr);
		});
		
		return Response.ok().entity(g.toJson(eventsMarkers)).build();
	}
	
	
	/**
	 * Method used to retrieve a list of the user's events
	 * Can be filtered by past, future, ongoing or all
	 * @param data - queryTime, at
	 * @return json list of events
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/eventsUser")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getUserEvents(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getUserEvents");
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		String username = authToken.getString("username");
		Entity user = (Entity) cacheSupport.cache.get(username);
		
		//Get user from database if not present in cache
		if( user == null ) {
			Key userKey = userKeyFactory.newKey(username);
			user = datastore.get(userKey);
		}

		if( user == null ) {
			LOG.warning("User doesn't exist on getEventsUser.");
			return Response.status(Status.NOT_FOUND).entity("Utilizador não existe.").build();
		}
		else
			cacheSupport.cache.put(user.getKey().getName(), user);
		
		List<Entity> eventsEntity = new ArrayList<Entity>();

		EntityQuery query = null;
		
		//Get list of events. -1 past, participated; 0 all; 1 future; 2 ongoing
		//Past, participated
		if( data.queryTime == -1 ) 
			query = Query.newEntityQueryBuilder()
					.setKind("UserEventsJoined")
					.setFilter(CompositeFilter.and(
							PropertyFilter.eq("username", username),
							PropertyFilter.eq("participated", true)))
					.build();
		//Future, not participated
		else if( data.queryTime == 1 ) 
			query = Query.newEntityQueryBuilder()
					.setKind("UserEventsJoined")
					.setFilter(CompositeFilter.and(
							PropertyFilter.eq("username", username),
							PropertyFilter.eq("participated", false)))
					.build();
		//All
		else 
			query = Query.newEntityQueryBuilder()
					.setKind("UserEventsJoined")
					.setFilter(PropertyFilter.eq("username", username))
					.build();
		
		//Run query
		QueryResults<Entity> tasks = datastore.run(query);
		
		//Add all queried events to list
		tasks.forEachRemaining(participation ->{
			String eventId = participation.getString("eventId");
			
			//Check for event in cache first, then go to database if needed
			Entity event = (Entity) cacheSupport.cache.get(eventId);
			if( event == null) {
				Key eventKey = eventKeyFactory.newKey(eventId);
				event = datastore.get(eventKey);
			}

			if( event != null ) {
				eventsEntity.add(event);
				cacheSupport.cache.putIfAbsent(eventId, event);
			}
		});
		
		
		List<Map<String, Value<?>>> events = new ArrayList<Map<String, Value<?>>>();
		
		//Filter out events based on given time choice
		switch(data.queryTime) {
			//Past events participated in
			case -1:
				for( Entity e : eventsEntity ) {
					//Get event properties and add to list of events			
					Map<String, Value<?>> props = new HashMap<String, Value<?>>();
					props.putAll(e.getProperties());
					
					//Add event to list
					events.add(props);
				}
				break;
			
			//All events
			case 0: 
				for( Entity e : eventsEntity ) {
					//Get event properties and add to list of events			
					Map<String, Value<?>> props = new HashMap<String, Value<?>>();
					props.putAll(e.getProperties());
					
					//Add event to list
					events.add(props);
				}
				break;
			
			//Future events
			case 1: 
				for( Entity e : eventsEntity ) {
					if( e.getLong("date_start") > System.currentTimeMillis()) {
						//Get event properties
						Map<String, Value<?>> props = new HashMap<String, Value<?>>();
						props.putAll(e.getProperties());
	
						//Add event to list
						events.add(props);
					}
				}
				break;
			
			//Ongoing events
			case 2: 
				for( Entity e : eventsEntity ) {
					if( e.getLong("date_end") > System.currentTimeMillis() && e.getLong("date_start") < System.currentTimeMillis() ) {
						//Get event properties
						Map<String, Value<?>> props = new HashMap<String, Value<?>>();
						props.putAll(e.getProperties());

						//Add event to list
						events.add(props);
					}
				}
				break;						
		}
		
		//Remove and add certain properties
		for( Map<String, Value<?>> props : events ) {
			//Add if user saved this event
			Value<?> saved = BooleanValue.of(false);
			String eventId = props.get("name").get().toString().replaceAll("\\s+", "").toLowerCase();

			Key savedEventKey = savedEventKeyFactory.newKey(eventId + authToken.getString("username"));
			Entity savedEvent = datastore.get(savedEventKey);
			
			if( savedEvent != null )
				saved = BooleanValue.of(true);
			
			props.put("saved", saved);
			
			props.remove("start_code");
		}
		
		return Response.ok().entity(g.toJson(events)).build();
	} 
	
	
	/**
	 * Method used by a user to get the GPS tracking of a route that he participated in
	 * @param data - name, at
	 * @return json list of markers
	 */
	@POST
	@Path("/eventRouteTrackingUser")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEventRouteTrackingUser(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getEventRouteTrackingUser");
		if( r.getStatus() != 200 ) {
			return r;
		}

		//Check event name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get event route tracking user with invalid event name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
		}
		
		String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
		Key eventKey = eventKeyFactory.newKey(eventId);
		Entity event = datastore.get(eventKey);
		
		//Check if event exists
		if( event == null ) {
			LOG.warning("Attempt to get tracking history of unexistent event.");
			return Response.status(Status.NOT_FOUND).entity("Evento não existe.").build();
		}
		
		//Check if user participated in event
		Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + authToken.getString("username"));
		Entity participation = datastore.get(participationKey);
		
		if( participation == null ) {
			LOG.warning("Attempt to get tracking of not participated in event.");
			return Response.status(Status.FORBIDDEN).entity("Não participou neste evento.").build();
		}

		//Prepare query that will get route markers
		EntityQuery query = Query.newEntityQueryBuilder()
				.setKind("RouteTracking")
				.setFilter(CompositeFilter.and(PropertyFilter.eq("event", eventId),
						PropertyFilter.eq("username", authToken.getString("username"))))
				.build();

		//Run query
		QueryResults<Entity> tasks = datastore.run(query);
		
		List<MapMarker> markers = new ArrayList<MapMarker>();	
		
		//Add properties to list
			tasks.forEachRemaining( marker -> {
			
			//Create marker and add to list
			MapMarker mrkr = new MapMarker(marker.getDouble("lat"), marker.getDouble("lng"), marker.getLong("time"));
			System.out.println(mrkr);
			markers.add(mrkr);
		});

		return Response.ok(g.toJson(markers)).build();
	} 
	
	
	/**
	 * Method used by a user to get the photos uploaded by him in an event
	 * @param data - name, at
	 * @return json list of photo links
	 */
	@POST
	@Path("/eventPhotosUser")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEventPhotosUser(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getEventPhotosUser");
		if( r.getStatus() != 200 ) {
			return r;
		}

		//Check event name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get event photos user with invalid event name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
		}
		
		String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
		Key eventKey = eventKeyFactory.newKey(eventId);
		Entity event = datastore.get(eventKey);
		
		//Check if event exists
		if( event == null ) {
			LOG.warning("Attempt to get event photos user of unexistent event.");
			return Response.status(Status.BAD_REQUEST).entity("Evento não existe.").build();
		}
		
		//Check if user participated in event
		Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + authToken.getString("username"));
		Entity participation = datastore.get(participationKey);
		
		if( participation == null ) {
			LOG.warning("Attempt to get event photos user of not participated in event.");
			return Response.status(Status.FORBIDDEN).entity("Não participou neste evento.").build();
		}
		
		//Prepare query that will get photos
		EntityQuery query = Query.newEntityQueryBuilder()
				.setKind("PhotoMarkerLog")
				.setFilter(CompositeFilter.and(
						PropertyFilter.eq("owner", authToken.getString("username")), 
						PropertyFilter.eq("eventId", eventId)))
				.build();

		//Run query
		QueryResults<Entity> tasks = datastore.run(query);
		
		List<String> links = new ArrayList<String>();	
		
		//Add links to list
		while( tasks.hasNext() ) {
			Entity photoLog = tasks.next();
			
			links.add(photoLog.getString("photoLink"));
		}

		return Response.ok(g.toJson(links)).build();
	} 
	
	
	/**
	 * Method used by a user to get the photos uploaded by him in a specific event marker
	 * @param data - name, markerId, at
	 * @return json list of photo links
	 */
	@POST
	@Path("/markerPhotosUser")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMarkerPhotosUser(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getMarkerPhotosUser");
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check event name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get event marker photos user with invalid event name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome do evento inválido.").build();
		}

		String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
		Key eventKey = eventKeyFactory.newKey(eventId);
		Entity event = datastore.get(eventKey);
		
		//Check if event exists
		if( event == null ) {
			LOG.warning("Attempt to get marker photos user of unexistent event.");
			return Response.status(Status.BAD_REQUEST).entity("Evento não existe.").build();
		}
		
		//Check marker id validity
		if( data.markerId == null || data.markerId.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get event marker photos user with invalid markerId.");
			return Response.status(Status.BAD_REQUEST).entity("Id inválido.").build();
		}
		
		String markerId = data.markerId.replaceAll("\\s+", "").toLowerCase();
		
		//Check if given marker exists
		KeyFactory mkKeyFactory = markerKeyFactory.addAncestor(PathElement.of("Event", eventId));
		Key markerKey = mkKeyFactory.newKey(markerId);
		if( datastore.get(markerKey) == null ) {
			LOG.warning("Attempt to get photos of unexistent event marker.");
			return Response.status(Status.NOT_FOUND).entity("Marcador especificado não existe.").build();
		}
		
		//Check if user participated in event
		Key participationKey = userEventsJoinedKeyFactory.newKey(eventId + authToken.getString("username"));
		Entity participation = datastore.get(participationKey);
		
		if( participation == null ) {
			LOG.warning("Attempt to get marker photos user of not participated in event.");
			return Response.status(Status.FORBIDDEN).entity("Não participou neste evento.").build();
		}
				
		//Prepare query that will get photos
		EntityQuery query = Query.newEntityQueryBuilder()
				.setKind("PhotoMarkerLog")
				.setFilter(CompositeFilter.and(
						PropertyFilter.eq("owner", authToken.getString("username")), 
						PropertyFilter.eq("markerId", markerId)))
				.build();

		//Run query
		QueryResults<Entity> tasks = datastore.run(query);
		
		List<String> links = new ArrayList<String>();	
		
		//Add links to list
		while( tasks.hasNext() ) {
			Entity photoLog = tasks.next();
			
			links.add(photoLog.getString("photoLink"));
		}

		return Response.ok(g.toJson(links)).build();
	} 
	
	
	/**
	 * Method used to get all photos of an event
	 * @param data - name, at
	 * @return json list of photo paths
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/photosEvent")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPhotosEvent(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getPhotosEvent");
		if( r.getStatus() != 200 ) {
			return r;
		}

		//Check event name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get event photos with invalid event name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome inválido.").build();
		}
		
		String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
		Entity event = (Entity) cacheSupport.cache.get(eventId);
		
		//Get event from database, if wasn't present in cache
		if( event == null ) {
			Key eventKey = eventKeyFactory.newKey(eventId);
			event = datastore.get(eventKey);
		}
		
		//Check if event exists in database
		if( event == null ) {
			LOG.warning("Attetmp to get photos of unexistent event.");
			return Response.status(Status.BAD_REQUEST).entity("Evento não existe.").build();
		}
		else
			cacheSupport.cache.put(eventId, event);
		
		//Prepare query that will get photos
		EntityQuery query = Query.newEntityQueryBuilder()
				.setKind("PhotoMarkerLog")
				.setFilter(PropertyFilter.eq("eventId", eventId))
				.build();

		//Run query
		QueryResults<Entity> tasks = datastore.run(query);
		
		List<String> links = new ArrayList<String>();	
		
		//Add links to list
		while( tasks.hasNext() ) {
			Entity photoLog = tasks.next();
			
			links.add(photoLog.getString("photoLink"));
		}

		return Response.ok(g.toJson(links)).build();
	} 
	
	
	/**
	 * Method used to get all photos of a marker of a given event
	 * @param data - name, markerId, at
	 * @return json list of photo paths
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/getPhotosMarker")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPhotosMarker(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getPhotosMarker");
		if( r.getStatus() != 200 ) {
			return r;
		}

		//Check event name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get marker photos with invalid event name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome inválido.").build();
		}
		
		String eventId = data.name.replaceAll("\\s+", "").toLowerCase();
		Entity event = (Entity) cacheSupport.cache.get(eventId);
		
		//Get event from database, if wasn't present in cache
		if( event == null ) {
			Key eventKey = eventKeyFactory.newKey(eventId);
			event = datastore.get(eventKey);
		}
		
		//Check if event exists in database
		if( event == null ) {
			LOG.warning("Attetmp to get photos of unexistent event.");
			return Response.status(Status.BAD_REQUEST).entity("Evento não existe.").build();
		}
		else
			cacheSupport.cache.put(eventId, event);
		
		//Check marker id validity
		if( data.markerId == null || data.markerId.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get marker photos with invalid marker id.");
			return Response.status(Status.BAD_REQUEST).entity("Id do marcador inválido.").build();
		}
		
		KeyFactory mkKeyFactory = markerKeyFactory.addAncestor(PathElement.of("Event", eventId));
		Key markerKey = mkKeyFactory.newKey(data.markerId);
		Entity marker = datastore.get(markerKey);
		
		//Check if given marker exists
		if( marker == null ) {
			LOG.warning("Attempt to get photos of unexistent map marker.");
			return Response.status(Status.BAD_REQUEST).entity("Marcador não existe.").build();
		}
		
		//Prepare query that will get photos
		EntityQuery query = Query.newEntityQueryBuilder()
				.setKind("PhotoMarkerLog")
				.setFilter(CompositeFilter.and(
						PropertyFilter.eq("markerId", data.markerId), 
						PropertyFilter.eq("eventId", eventId)) )
				.build();

		//Run query
		QueryResults<Entity> tasks = datastore.run(query);
		
		List<String> links = new ArrayList<String>();	
		
		//Add links to list
		while( tasks.hasNext() ) {
			Entity photoLog = tasks.next();
			
			links.add(photoLog.getString("photoLink"));
		}

		return Response.ok(g.toJson(links)).build();
	} 
	
	
	/**
	 * This method ended up a mess, was a last minute change that broke it
	 * 
	 * Method used to retrieve the events of a specific institution
	 * Events can be filtered by past, future or all
	 * @param data - name, at
	 * @return json list of events, cursor
	 */
	@POST
	@Path("/eventsInstitution")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getInstitutionEvents(QueryData data) {
		
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "eventsInstitution");
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check institution name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get inst events with invalid inst name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome inválido.").build();
		}
		
		//Get institution
		String instName = data.name.replaceAll("\\s+", "").toLowerCase();
		Key instKey = institutionKeyFactory.newKey(instName);
		Entity institution = datastore.get(instKey);
		
		//Get all events of an institution
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("Event");
				
		//Prepare time filter
		PropertyFilter filter = null;
		if( data.queryTime == -1 )
			filter = PropertyFilter.lt("date_end", System.currentTimeMillis());
		else if ( data.queryTime == 0 || data.queryTime == 2 )
			filter = null;
		else if ( data.queryTime == 1 )
			filter = PropertyFilter.gt("date_start", System.currentTimeMillis());
			
		//Add institution name filter
		if( filter != null )
			query.setFilter(CompositeFilter.and(PropertyFilter.eq("institution", instName), filter));
		else
			query.setFilter(PropertyFilter.eq("institution", instName));
		
		QueryResults<Entity> tasks = datastore.run(query.build());

		List<Map<String, Value<?>>> events = new ArrayList<Map<String, Value<?>>>();
		
		String username = authToken.getString("username");
		
		tasks.forEachRemaining(e -> {
			//Get event properties
			Map<String, Value<?>> props = new HashMap<String, Value<?>>();
			props.putAll(e.getProperties());
			
			if( data.queryTime == 2 ) {
				if( e.getLong("date_start") < System.currentTimeMillis() && e.getLong("date_end") > System.currentTimeMillis() ) 
					events.add(props);
			}
			else 
				events.add(props);
			
			if( !username.equalsIgnoreCase(institution.getString("owner")) ) {
				props.remove("start_code");
			}
		});
		
		return Response.ok(g.toJson(events)).build();	
	} 
	

	/**
	 * Method used to retrieve information about a specific institution
	 * @param data - name, at
	 * @return json with properties 
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/institution")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getInstitution(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getInstitution");
		if( r.getStatus() != 200 ) {
			return r;
		}
	
		//Check institution name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get inst with invalid inst name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome inválido.").build();
		}
		
		String instName = data.name.replaceAll("\\s+", "").toLowerCase();
		Entity inst = (Entity) cacheSupport.cache.get(instName);
		
		//Get institution from database if not present in cache
		if( inst == null ) {
			Key instKey = institutionKeyFactory.newKey(instName);
			inst = datastore.get(instKey);
		}

		//Check if inst not null
		if( inst == null )
			return Response.status(Status.NOT_FOUND).entity("Instituição não existe.").build();
		else
			cacheSupport.cache.put(inst.getKey().getName(), inst);

		return Response.ok(g.toJson(inst.getProperties())).build();
	} 
	
	
	/**
	 * Method used to retrieve owned institution
	 * @param data - at
	 * @return json with properties 
	 */
	@POST
	@Path("/institutionOwned")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getOwnInstitution(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getOwnInstitution");
		if( r.getStatus() != 200 ) {
			return r;
		}
	
		Map<String, Value<?>> institution = new HashMap<String, Value<?>>();
		
		//Query to get owned institution
		EntityQuery query = Query.newEntityQueryBuilder()
				.setKind("Institution")
				.setFilter(PropertyFilter.eq("owner", authToken.getString("username")))
				.build();
	
		//Get institutions
		QueryResults<Entity> tasks = datastore.run(query);
		
		//Check if user is owner of any institution
		if( !tasks.hasNext() )
			return Response.status(Status.BAD_REQUEST).entity("Não existe nenhuma instituição.").build();
		else
			institution = tasks.next().getProperties();

		return Response.ok(g.toJson(institution)).build();
	} 


	/**
	 * Method used to get a list of all institutions
	 * @param data - cursor, at
	 * @return json list of all institution properties
	 */
	@POST
	@Path("/institutionsAll")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getInstitutionsAll(QueryData data) {
		
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);
		
		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getInstitutionsAll");
		if( r.getStatus() != 200 ) {
			return r;
		}
				
		Cursor startCursor = null;
		
		if( data.startCursorString != null && !data.startCursorString.equals("") ) {
			//Where query stopped last time
			startCursor = Cursor.fromUrlSafe(data.startCursorString);
		}
		
		List<Map<String, Value<?>>> institutions = new ArrayList<Map<String, Value<?>>>();
		
		//Prepare query that will get institutions
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("Institution")
				.setLimit(PAGE_SIZE)
				.setStartCursor(startCursor);
	
		//Get institutions
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		tasks.forEachRemaining( inst -> {
			institutions.add(inst.getProperties());
		});
		
		//Where to start next time
		Cursor cursor = tasks.getCursorAfter();
		if( cursor != null && institutions.size() == PAGE_SIZE ) {
			String cursorString = cursor.toUrlSafe();
			Pair p = new Pair(institutions, cursorString);
			return Response.ok(g.toJson(p)).build();
		}
		else {
			Pair p = new Pair(institutions, "end");
			return Response.ok(g.toJson(p)).build();
		}
	} 


	/**
	 * Method used to retrieve properties of a group
	 * @param data - name, at
	 * @return json list of group properties
	 *//*
	@POST
	@Path("/group")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getGroup(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);
		
		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getGroup");
		if( r.getStatus() != 200 ) {
			return r;
		}
	
		String groupName = data.name.replaceAll("\\s+", "").toLowerCase();
		Entity group = (Entity) cacheSupport.cache.get(groupName);

		//Get group from database, if wasn't present in cache
		if( group == null ) {
			Key groupKey = groupKeyFactory.newKey(groupName);
			group = datastore.get(groupKey);
			cacheSupport.cache.put(groupKey.getName(), group);
		}

		if( group == null )
			return Response.status(Status.NOT_FOUND).entity("Grupo não existe.").build();

		return Response.ok(g.toJson(group.getProperties())).build();
	} */

	
	/**
	 * Method used to retrieve a list of all groups
	 * @param data - at
	 * @return json list of all group properties
	 *//*
	@POST
	@Path("/groupsAll")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllGroups(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "groupsAll");
		if( r.getStatus() != 200 ) {
			return r;
		}
				
		Cursor startCursor = null;
		
		if( data.startCursorString != null && !data.startCursorString.equals("") ) {
			//Where query stopped last time
			startCursor = Cursor.fromUrlSafe(data.startCursorString);
		}
		
		List<Map<String, Value<?>>> groups = new ArrayList<Map<String, Value<?>>>();
		
		//Prepare query that will get groups
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("Group")
				.setLimit(PAGE_SIZE)
				.setStartCursor(startCursor);
	
		//Get groups
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		while( tasks.hasNext() ) {
			Entity group = tasks.next();
			groups.add(group.getProperties());
			
			//Add to cache if not present
			cacheSupport.cache.put(group.getKey().getName(), group);
		}
		
		//Where to start next time
		Cursor cursor = tasks.getCursorAfter();
		if( cursor != null && groups.size() == PAGE_SIZE ) {
			String cursorString = cursor.toUrlSafe();
			Pair p = new Pair(groups, cursorString);
			return Response.ok(g.toJson(p)).build();
		}
		else {
			Pair p = new Pair(groups, "end");
			return Response.ok(g.toJson(p)).build();
		}
	} */

	
	/**
	 * Method used to retrieve a list of all comments of an event.
	 * Can be sorted by ascending or descending time
	 * @param data - name, at
	 * @return json list of comments, cursor
	 */
	@POST
	@Path("/comments")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getComments(QueryData data) {
		
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getComments");
		if( r.getStatus() != 200 ) {
			return r;
		}
			
		Cursor startCursor = null;
		
		if( data.startCursorString != null && !data.startCursorString.equals("") ) {
			//Where query stopped last time
			startCursor = Cursor.fromUrlSafe(data.startCursorString);
		}
		
		//Check event name validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get comments with invalid event name.");
			return Response.status(Status.BAD_REQUEST).entity("Nome inválido.").build();
		}
		
		List<Map<String, Value<?>>> comments = new ArrayList<Map<String, Value<?>>>();
		String eventName = data.name.replaceAll("\\s+", "").toLowerCase();
		
		//Prepare query that will get comments
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("Comment")
				.setFilter(PropertyFilter.hasAncestor(eventKeyFactory.newKey(eventName)))
				.setLimit(PAGE_SIZE)
				.setStartCursor(startCursor);
	
		//Set order asc or desc
		if( data.newFirst == false )
			query.setOrderBy(OrderBy.asc("date"));
		else
			query.setOrderBy(OrderBy.desc("date"));
		
		//Get comments
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		while( tasks.hasNext() ) {
			Entity task = tasks.next();
			comments.add(task.getProperties());
		}
		
		//Where to start next time
		Cursor cursor = tasks.getCursorAfter();
		if( cursor != null && comments.size() == PAGE_SIZE ) {
			String cursorString = cursor.toUrlSafe();
			Pair p = new Pair(comments, cursorString);
			return Response.ok(g.toJson(p)).build();
		}
		else {
			Pair p = new Pair(comments, "end");
			return Response.ok(g.toJson(p)).build();
		}
	} 

	
	/**
	 * Method used to retrieve a list of top users
	 * @param data - at
	 * @return json list of top users
	 */ 
	@POST
	@Path("/topUsers")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTopUsers(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getTopUsers");
		if( r.getStatus() != 200 ) {
			return r;
		}
			
		List<Map<String, Value<?>>> users = new ArrayList<Map<String, Value<?>>>();
		
		//Prepare query that will get events
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("User")
				.setOrderBy(OrderBy.desc("scorePoints"))
				.setLimit(TOPS_SIZE);
	
		//Get top users
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		//Build properties for each user
		tasks.forEachRemaining(user -> {
			String username = user.getString("username").toLowerCase();
			KeyFactory profileKeyFactoryAncestor = datastore.newKeyFactory().setKind("Profile").addAncestor(PathElement.of("User", username));
			Key profileKey = profileKeyFactoryAncestor.newKey(username);
			Entity profile = datastore.get(profileKey);
			
			//Get user properties
			Map<String, Value<?>> props = new HashMap<String, Value<?>>();
			props.putAll(user.getProperties());
			props.putAll(profile.getProperties());
			
			//Remove certain properties
			props.remove("password");
			props.remove("email");
			props.remove("creation_time");
			props.remove("shopPoints");
			users.add(props);
		});
		
		
		return Response.ok(g.toJson(users)).build();
	} 
	
	
	/**
	 * Method used to retrieve notifications.
	 * This must be called periodically in the frontend to be effective
	 * @param data - at
	 * @return json list of notifications
	 */
	@POST
	@Path("/notifications")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getNotifications(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getNotifications");
		if( r.getStatus() != 200 ) {
			return r;
		}
			
		List<String> notifications = new ArrayList<String>();
		
		//Prepare query that will get notifications
		EntityQuery query = Query.newEntityQueryBuilder()
				.setKind("Notification")
				.setFilter(CompositeFilter.and(
						PropertyFilter.eq("username", authToken.getString("username")), 
						PropertyFilter.eq("delivered", false)) )
				.build();
	
		//Get notifications
		QueryResults<Entity> tasks = datastore.run(query);
		
		while( tasks.hasNext() ) {
			Entity notif = tasks.next();
			notifications.add(notif.getString("text"));
			
			//Set delivered to true 
			notif = Entity.newBuilder(notif)
				.set("delivered", true)
				.build();
			datastore.put(notif);
			
		}
		
		return Response.ok(g.toJson(notifications)).build();
	} 
	
	
	/**
	 * Method used by BO or SU to retrieve logs of the database
	 * Can be filtered by a specified method
	 * @param data - method, at
	 * @return json list of logs
	 */
	@POST
	@Path("/logs")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getLogs(QueryData data) {

		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getLogs");
		if( r.getStatus() != 200 ) {
			return r;
		}
			
		//Prepare query that will get logs
		//Not ordering by time, because that would imply needing to add composite index for all used methods
		//On web, user can simply sort the table
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("Log");
		
		//If specified a method, get only those
		if( data.method != null && data.method != "" )
			query.setFilter(PropertyFilter.eq("method", data.method.replaceAll("\\s+", "").toLowerCase()));
		
		//Run query to get logs
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		List<Map<String, Value<?>>> logs = new ArrayList<Map<String, Value<?>>>();
		
		//Add logs to list 
		tasks.forEachRemaining(log -> {
			logs.add(log.getProperties());
		});
		
		return Response.ok(g.toJson(logs)).build();
	} 
	
	
	/**
	 * Method used to retrieve a list of shop items
	 * Currently returns unpurchased shop items by the user, and items with stock
	 * @param data - at 
	 * @return json list of shop items
	 */
	@POST
	@Path("/shopItems")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getShopItems(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getShopItems");
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		List<Map<String, Value<?>>> shopItemsToSend = new ArrayList<Map<String, Value<?>>>();
		List<String> userPurchases = new ArrayList<String>();
		
		//Prepare query that will get shop items with some stock (>0)
		EntityQuery.Builder queryShop = Query.newEntityQueryBuilder()
				.setKind("Shop")
				.setFilter(PropertyFilter.gt("quantity", 0));
		
		//Run query
		QueryResults<Entity> taskShop = datastore.run(queryShop.build());
		
		
		//Prepare query that will get shop items with some stock (>0)
		EntityQuery.Builder queryPurchases = Query.newEntityQueryBuilder()
				.setKind("ShopPurchase")
				.setFilter(PropertyFilter.eq("username", authToken.getString("username")));
		
		//Run query
		QueryResults<Entity> taskPurchases = datastore.run(queryPurchases.build());
		
		//Add all user's purchased items to list
		taskPurchases.forEachRemaining(item ->{
			userPurchases.add(item.getString("shopItemId"));
		});
		
		//Add properties to list, but only if user hasn't already purchased
		while( taskShop.hasNext() ) {
			Entity shopItem = taskShop.next();
			String shopItemId = shopItem.getKey().getName();
			
			//Check if already purchased
			if( !userPurchases.contains(shopItemId) ) {
				//Get shop item properties
				Map<String, Value<?>> props = new HashMap<String, Value<?>>();
				props.putAll(shopItem.getProperties());
				
				shopItemsToSend.add(props);
			}
		}
		
		return Response.ok(g.toJson(shopItemsToSend)).build();
				
	} 

	
	/**
	 * Method used to retrieve a list of ALL shop items
	 * Used by BO and SU users to check on items and edit if needed
	 * @param data - at 
	 * @return json list of shop items
	 */
	@POST
	@Path("/shopItemsAll")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllShopItems(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getAllShopItems");
		if( r.getStatus() != 200 ) {
			return r;
		}

		List<Map<String, Value<?>>> items = new ArrayList<Map<String, Value<?>> >();
		
		//Prepare query that will get shop items
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("Shop");
		
		//Run query
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		//Add properties to list
		while( tasks.hasNext() ) {
			Entity shopItem = tasks.next();

			//Get shop item properties
			Map<String, Value<?>> props = new HashMap<String, Value<?>>();
			props.putAll(shopItem.getProperties());
			
			items.add(props);
			
		}
		
		return Response.ok(g.toJson(items)).build();
				
	}	
	
	
	/**
	 * Method used by a user to retrieve a list of his saved/favorited events
	 * @param data - at
	 * @return json list of events
	 */
	@SuppressWarnings("unchecked")
	@POST
	@Path("/savedEvents")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSavedEvents(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getSavedEvents");
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		String username = authToken.getString("username");
		Entity user = (Entity) cacheSupport.cache.get(username);
		
		//Get user from database, if wasn't present in cache
		if( user == null ) {
			Key userKey = userKeyFactory.newKey(username);
			user = datastore.get(userKey);
		}
		
		//Check if user exists in database
		if( user == null ) {
			LOG.warning("Attempt to get saved events with null user.");
			return Response.status(Status.NOT_FOUND).entity("Utilizador não existe.").build();
		}
		else
			cacheSupport.cache.put(username, user);
				
		List<Map<String, Value<?>>> savedEvents = new ArrayList<Map<String, Value<?>>>();		
		
		//Prepare query that will get saved events
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("SavedEvent")
				.setFilter(PropertyFilter.eq("username", username));
		
		//Run query
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		tasks.forEachRemaining(eventSaved -> {
			String eventId = eventSaved.getString("eventName").replaceAll("\\s+", "").toLowerCase();
			Key eventKey = eventKeyFactory.newKey(eventId);
			Entity event = datastore.get(eventKey);

			//If event exists, add to list to return to front end
			if( event != null ) {
				//Get event properties
				Map<String, Value<?>> props = new HashMap<String, Value<?>>();
				props.putAll(datastore.get(eventKey).getProperties());
						
				//Remove certain properties
				props.remove("start_code");
				
				//Add if user is in event, used only in one situation on the web for a specific button
				Value<?> joined = BooleanValue.of(false);
				
				Key participationKey = userEventsJoinedKeyFactory.newKey(event.getKey().getName() + authToken.getString("username"));
				Entity participation = datastore.get(participationKey);
				
				if( participation != null )
					joined = BooleanValue.of(true);
				
				props.put("inEvent", joined);
								
				savedEvents.add(props);
			}
		});

		return Response.ok(g.toJson(savedEvents)).build();
	} 
	

	/**
	 * Method used to retrieve a list of top rated events
	 * @param data - at
	 * @return json list of events
	 */
	@POST
	@Path("/topRatedEvents")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTopRatedEvents(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "topRatedEvents");
		if( r.getStatus() != 200 ) {
			return r;
		}

		//Prepare query that will get events
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setKind("Event")
				.setOrderBy(OrderBy.desc("actualRating"))
				.setLimit(PAGE_SIZE);
		
		//Run query
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		List<Map<String, Value<?>>> events = new ArrayList<Map<String, Value<?>>>();	
		
		//Add properties to list
		while( tasks.hasNext() ) {
			Entity event = tasks.next();
			
			//Get event item properties
			Map<String, Value<?>> props = new HashMap<String, Value<?>>();
			props.putAll(event.getProperties());
			
			//Remove certain properties
			props.remove("start_code");
			
			events.add(props);
		}

		return Response.ok(g.toJson(events)).build();
	} 
	

	/**
	 * Method used by a BO or SU user to retrieve a list of unchecked suggestions/reports/bugs
	 * @param data - name, at
	 * @return json list of events
	 */
	@POST
	@Path("/suggestionsOrReports")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSuggestionsOrReports(QueryData data) {
	
		//Get authToken, either from cache, or database
		Entity authToken = getAuthToken(data.at.tokenID);

		//Check login
		Response r = checkLogin( data.at, authToken);
		if( r.getStatus() != 200 ) {
			return r;
		}
		
		//Check RBAC
		r = checkRBAC(authToken.getString("role"), "getSuggestionsOrReports");
		if( r.getStatus() != 200 ) {
			return r;
		}

		//Check type validity
		if( data.name == null || data.name.replaceAll("\\s+", "").equals("") ) {
			LOG.warning("Attempt to get logs with invalid type.");
			return Response.status(Status.BAD_REQUEST).entity("Tipo inválido.").build();
		}
				
		//Prepare query that will get unchecked suggestions/reports/bugs
		EntityQuery.Builder query = Query.newEntityQueryBuilder()
				.setFilter(PropertyFilter.eq("checked", false));
		
		//Check which to retrieve
		if( data.name.equalsIgnoreCase(SUGGESTION) )
			query.setKind(SUGGESTION);
		else if( data.name.equalsIgnoreCase(REPORT) )
			query.setKind(REPORT);
		else if( data.name.equalsIgnoreCase(BUG) )
			query.setKind(BUG);
		else
			return Response.status(Status.BAD_REQUEST).entity("Tipo especificado não existe.").build();
		
		//Run query
		QueryResults<Entity> tasks = datastore.run(query.build());
		
		List<Map<String, Value<?>>> srbs = new ArrayList<Map<String, Value<?>>>();	
		
		//Add properties to list
		while( tasks.hasNext() ) {
			Entity srb = tasks.next();
			
			//Get event item properties
			Map<String, Value<?>> props = new HashMap<String, Value<?>>();
			props.putAll(srb.getProperties());
			
			srbs.add(props);
		}

		return Response.ok(g.toJson(srbs)).build();
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
	 * Check if a user is in a session by doing some checks on the authToken
	 * @param txn - active transaction from where this method was called 
	 * @param at - sent token, check if not null
	 * @param authToken - token from database, check validity
	 * @return response message with status
	 */
	@SuppressWarnings("unchecked")
	private Response checkLogin(AuthToken at, Entity authToken) {
		//Check both given token and database token
		if( at == null || authToken == null ) {
			LOG.warning("Attempt to operate with no login.");
			return Response.status(Status.UNAUTHORIZED).entity("Login não encontrado.").build();
		}
		
		//If token is found, check for validity
		if( authToken.getLong("expirationDate") < System.currentTimeMillis() ) {
			LOG.warning("Auth Token expired.");
			datastore.delete(authToken.getKey());
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
	private Response checkRBAC(String role, String methodName) {
		Key methodKey = rbacKeyFactory.newKey(methodName.toLowerCase());
		Entity method = datastore.get(methodKey);
		
		//Check if method exists
		if( method == null ) {
			return Response.status(Status.NOT_FOUND).entity("Método especificado não existe.").build();
		}
		
		//Check RBAC
		if( method.getBoolean(role) == false ) {
			LOG.warning("User doesn't have permission for this action.");
			return Response.status(Status.FORBIDDEN).entity("Você não tem permissão para realizar esta ação.").build();
		} 
		else
			return Response.ok().build();
	}
	
	
	
}
