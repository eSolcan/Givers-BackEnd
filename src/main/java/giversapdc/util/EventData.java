package giversapdc.util;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;

public class EventData {

	private static final String REGISTER = "register";
	private static final String EDIT = "edit";
	
	//We understand that this solution is sub-optimal, and that we should have put these on the database, 
	//but due to time constraints, and the not-so-useful nature of interests, we decided to go with a static list
	private static final String[] INTERESTS = {"ambiente", "animais", "contraafome", "crianças", "idosos", "sem-abrigo", "refugiados", "outros"};
	
	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	
	public String institutionName;
	
	public String name;
	public String interests;
	public String address;
	public long dateStart;
	public String duration;
	public int capacity;
	public String description;
	public MapMarker[] markers;
	public String joinCode;
	public double rating;
	public String markerId;
	public String photoLink;
	
	public String password;
	public String passwordConfirm;
	
	public byte[] photo;
	
	public AuthToken at;
	
	public String startCursorString;
	
	//public boolean group;		//Used to indicate if joining as group or user
	public String groupName;	

	Key eventMinNameKey = codeValueKeyFactory.newKey("mineventname");
	long eventMinName = datastore.get(eventMinNameKey).getLong("value");
	
	Key eventMaxNameKey = codeValueKeyFactory.newKey("maxeventname");
	long eventMaxName = datastore.get(eventMaxNameKey).getLong("value"); 
	
	Key eventMinDurationKey = codeValueKeyFactory.newKey("eventminduration");
	long eventMinDuration = datastore.get(eventMinDurationKey).getLong("value") / (60*1000); 
	
	Key addressMinSizeKey = codeValueKeyFactory.newKey("addressminsize");
	long addressMinSize = datastore.get(addressMinSizeKey).getLong("value");
	
	Key eventMinStartKey = codeValueKeyFactory.newKey("mineventstart");
	long eventMinStart = datastore.get(eventMinStartKey).getLong("value") /(60*1000); 
	
	Key descriptionMinSizeKey = codeValueKeyFactory.newKey("descriptionminsize");
	long descriptionMinSize = datastore.get(descriptionMinSizeKey).getLong("value");
	
	Key descriptionMaxSizeKey = codeValueKeyFactory.newKey("descriptionmaxsize");
	long descriptionMaxSize = datastore.get(descriptionMaxSizeKey).getLong("value");
	
	Key routeMaxMarkersKey = codeValueKeyFactory.newKey("routemaxmarkers");
	long routeMaxMarkers = datastore.get(routeMaxMarkersKey).getLong("value");
	
	
	public EventData() { }
	
	public Response validDataRegister() {
		if( !validName() )
			return Response.status(Status.BAD_REQUEST).entity("Nome deve conter entre " + eventMinName + " e " + eventMaxName + " caracteres.").build();
		else if( !validAddress(REGISTER) )
			return Response.status(Status.BAD_REQUEST).entity("Morada deve conter pelo menos " + addressMinSize + " caracteres.").build();
		else if( !validDateStart(REGISTER) )
			return Response.status(Status.BAD_REQUEST).entity("Data de início deve ser pelo menos daqui a " + eventMinStart + " minutos.").build();
		else if( !validCapacity(REGISTER) )
			return Response.status(Status.BAD_REQUEST).entity("Capacidade deve ser maior que 0.").build();
		else if( !validDescription(REGISTER) )
			return Response.status(Status.BAD_REQUEST).entity("Descrição deve conter entre " + descriptionMinSize + " e " + descriptionMaxSize + " catacteres.").build();
		else if( !validMarkers(REGISTER) )
			return Response.status(Status.BAD_REQUEST).entity("Deverá selecionar entre 1 e " + routeMaxMarkers + " localizações no mapa.").build();
		else if( !validInterest() )
			return Response.status(Status.BAD_REQUEST).entity("Interesse inválido, selecione um da lista indicada.").build();
		else if( !validDuration() )
			return Response.status(Status.BAD_REQUEST).entity("Duração mínima é " + eventMinDuration + " minutos.").build();
		else
			return Response.ok().build();
	}
	
	public Response validDataEdit() {
		if( !validAddress(EDIT) )
			return Response.status(Status.BAD_REQUEST).entity("Morada deve conter pelo menos " + addressMinSize + " caracteres.").build();
		else if( !validDateStart(EDIT) )
			return Response.status(Status.BAD_REQUEST).entity("Data de início deve ser pelo menos daqui a " + eventMinStart + " minutos.").build();
		else if( !validCapacity(EDIT) )
			return Response.status(Status.BAD_REQUEST).entity("Capacidade deve ser maior que 0.").build();
		else if( !validDescription(EDIT) )
			return Response.status(Status.BAD_REQUEST).entity("Descrição deve conter entre " + descriptionMinSize + " e " + descriptionMaxSize + " catacteres.").build();
		else if( !validInterest() )
			return Response.status(Status.BAD_REQUEST).entity("Interesse inválido, selecione um da lista indicada.").build();
		else if( !validMarkers(EDIT) )
			return Response.status(Status.BAD_REQUEST).entity("Deverá selecionar entre 1 e " + routeMaxMarkers + " localizações no mapa.").build();
		else if( !validDuration() )
			return Response.status(Status.BAD_REQUEST).entity("Duração mínima é " + eventMinDuration + " minutos.").build();
		else
			return Response.ok().build();
	}
	
	
	public boolean validName() {
		return this.name != null && this.name.replaceAll("\\s+", "").length() >= eventMinName && this.name.replaceAll("\\s+", "").length() <= eventMaxName;
	}
		
	public boolean validAddress(String type) {
		if( this.address == null )
			return false;
		else
			return this.address.replaceAll("\\s+", "").length() >= addressMinSize;
	}
	
	public boolean validDateStart(String type) {
		return this.dateStart >= System.currentTimeMillis() + eventMinStart ;
	}
	
	public boolean validCapacity(String type) {
		return capacity > 0;
	}
	
	public boolean validDescription(String type) {
		if( this.description == null || this.description.equals("") )
			return false;
		else
			return this.description.replaceAll("\\s+", "").length() >= descriptionMinSize && 
				this.description.replaceAll("\\s+", "").length() <= descriptionMaxSize;
	}
	
	public boolean validMarkers(String type) {
		return this.markers != null && this.markers.length > 0 && this.markers.length < routeMaxMarkers;
	}
	
	public boolean validInterest() {
		if( this.interests == null )
			return false;			
		else {
			String interest = this.interests.replaceAll("\\s+", "").toLowerCase();
			for( String i : INTERESTS ) {
				if( interest.equals(i) )
					return true;
			}
		}
		return false;
	}
	
	public boolean validDuration() {
		//Parse time, comes in as String of this format HH:MM
		int hours = Integer.parseInt(this.duration.split(":")[0]);
		int minutes = Integer.parseInt(this.duration.split(":")[1]);
		
		long duration = (long) ((hours*60 + minutes) * 60 * 1000);
		
		return duration >= eventMinDuration;
	}
	
	
}
