package giversapdc.util;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;

public class LoginData {

	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	
	public String username;		
	public String password;
	
	public AuthToken at;
	
	public LoginData() { }
	
	public boolean validUsername() {
		
		Key minLengthUsernameKey = codeValueKeyFactory.newKey("minlengthusername");
		long minLengthUsername = datastore.get(minLengthUsernameKey).getLong("value");
		
		return this.username.replaceAll("\\s+", "").length() >= minLengthUsername;
	}
}
