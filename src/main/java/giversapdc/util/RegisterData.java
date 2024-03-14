package giversapdc.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;

public class RegisterData {

	//Might want to change this to a stronger verification, for specific domains, for example
	private static final Pattern VALID_EMAIL_ADDRESS_REGEX = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,3}$", Pattern.CASE_INSENSITIVE);
	
	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	
	public String username;
	public String email;
	public String name;
	public String password;
	public String passwordConfirm;
	public AuthToken at;
	public String code;
	
	public RegisterData() { }
	
	public Response validRegisterData(Transaction txn) { 

		Key passMinLengthKey = codeValueKeyFactory.newKey("passwordminlength");
		long passMinLength = txn.get(passMinLengthKey).getLong("value");

		Key minLengthUsernameKey = codeValueKeyFactory.newKey("minlengthusername");
		long minLengthUsername = txn.get(minLengthUsernameKey).getLong("value");		

		Key minLengthNameKey = codeValueKeyFactory.newKey("minlengthname");
		long minLengthName = txn.get(minLengthNameKey).getLong("value");
		
		//Weak passwords, ideally would have at least letters+numbers required
		if( !validUsername(minLengthUsername) )
			return Response.status(Status.BAD_REQUEST).entity("Username deve conter pelo menos " + minLengthUsername + " caracteres.").build();
		else if( !validPassword(passMinLength) )		
			return Response.status(Status.BAD_REQUEST).entity("Password deve conter pelo menos " + passMinLength + " caracteres.").build();
		else if( !validPasswordConf() )
			return Response.status(Status.BAD_REQUEST).entity("Password e confirmação não são idênticas.").build();
		else if( !validEmail())
			return Response.status(Status.BAD_REQUEST).entity("Email deve ter o seguinte formato *****@*****.*** .").build();
		else if( !validName(minLengthName) )
			return Response.status(Status.BAD_REQUEST).entity("Nome deve conter pelo menos " + minLengthName + " caracteres.").build();
		else
			return Response.ok().build();
	}
		
	public boolean validUsername(long minLength) {
		return this.username != null && this.username.replaceAll("\\s+", "").length() >= minLength;
	}
	
	public boolean validName(long minLength) {
		return this.name != null && this.name.replaceAll("\\s+", "").length() >= minLength;
	}
	
	public boolean validPassword(long minLength) {
		return this.password != null && this.password.replaceAll("\\s+", "").length() >= minLength;
	}
	
	public boolean validPasswordConf() {
		return this.passwordConfirm != null && this.passwordConfirm.equals(this.password);
	}
	
	public boolean validEmail() {
		Matcher matcher = null;
		if( this.email != null )
			matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(this.email);
		else
			return false;
		return !this.email.equals("") && matcher.find();
	}
}
