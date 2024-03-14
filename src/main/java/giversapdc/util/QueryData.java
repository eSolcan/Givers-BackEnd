package giversapdc.util;

public class QueryData {

	//We understand that this solution is sub-optimal, and that we should have put these on the database, 
	//but due to time constraints, and the not-so-useful nature of interests, we decided to go with a static list
	private static final String[] INTERESTS = {"ambiente", "animais", "contraafome", "crianças", "idosos", "sem-abrigo", "refugiados", "outros"};
	
	public String name;
	public String interests;
	public boolean newFirst;
	public int time;
	public String method;
	public double lat;
	public double lng;
	
	public String markerId;
	
	public AuthToken at;
	
	public String startCursorString;
	
	//This var is used for the query of events to select which time to show: -1 ended, 0 all, 1 future, 2 ongoing
	public int queryTime;
	
	public QueryData() { }
	
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
	
}
