package giversapdc.util;

public class MapMarker {

	public String markerId;
	public double lat;
	public double lng;
	public String eventId;
	public String eventName;
	public String description;
	public boolean risk;
	
	public long time;
	
	public MapMarker() { }
	
	public MapMarker(String id, double lat, double lng, String eventId, String eventName, String description, boolean risk) {
		this.markerId = id;
		this.lat = lat;
		this.lng = lng;
		this.eventId = eventId;
		this.eventName = eventName;
		this.description = description;
		this.risk = risk;
	}
	
	public MapMarker(double lat, double lng, String eventId, String eventName, String description) {
		this.lat = lat;
		this.lng = lng;
		this.eventId = eventId;
		this.eventName = eventName;
		this.description = description;
	}
	
	public MapMarker(double lat, double lng, long time) {
		this.lat = lat;
		this.lng = lng;
		this.time = time;
	}
	
	
}
