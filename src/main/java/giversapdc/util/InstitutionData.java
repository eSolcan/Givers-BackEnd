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

public class InstitutionData {

	private static final Pattern VALID_EMAIL_ADDRESS_REGEX = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,3}$", Pattern.CASE_INSENSITIVE);
	private static final Pattern VALID_PHONE_NR_REGEX = Pattern.compile("(9[01236]\\d) ?(\\d{3}) ?(\\d{3})");
	
	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
		
	public String name;
	public String email;
	public String phoneNr;
	public String address;
	public double lat;
	public double lon;
	
	public String password;
	public String passwordConfirm;
	
	//public String photo;
	public byte[] photo;
	
	public AuthToken at;
	
	public InstitutionData() { }
	
	public Response validDataRegister(Transaction txn) {
		
		Key minInstNameKey = codeValueKeyFactory.newKey("mininstname");
		long minInstName = txn.get(minInstNameKey).getLong("value");
		
		Key addressMinSizeKey = codeValueKeyFactory.newKey("addressminsize");
		long addressMinSize = txn.get(addressMinSizeKey).getLong("value");
		
		if( !validName(minInstName) ) 
			return Response.status(Status.BAD_REQUEST).entity("Nome da instituição deverá ter pelo menos " + minInstName + " caracteres.").build();
		else if( !validEmail() ) 
			return Response.status(Status.BAD_REQUEST).entity("Email deverá ter formato ***@***.*** .").build();
		else if( !validPhoneNr() ) 
			return Response.status(Status.BAD_REQUEST).entity("Número de telefone deverá ter 9 algarismos e ser válido.").build();
		else if( !validAddress(addressMinSize) ) 
			return Response.status(Status.BAD_REQUEST).entity("Morada deverá ter pelo menos " + addressMinSize + " caracteres.").build();
		else
			return Response.ok().build();
	}
	
	public Response validDataEdit(Transaction txn) {
		
		Key addressMinSizeKey = codeValueKeyFactory.newKey("addressminsize");
		long addressMinSize = txn.get(addressMinSizeKey).getLong("value");
		
		if( !validEmail() ) 
			return Response.status(Status.BAD_REQUEST).entity("Email deverá ter formato ***@***.*** .").build();
		else if( !validPhoneNr() ) 
			return Response.status(Status.BAD_REQUEST).entity("Número de telefone deverá ter 9 algarismos e ser válido.").build();
		else if( !validAddress(addressMinSize) ) 
			return Response.status(Status.BAD_REQUEST).entity("Morada deverá ter pelo menos " + addressMinSize + " caracteres.").build();
		else
			return Response.ok().build();
	}
		
	public boolean validName(long minSize) {
		return this.name != null && this.name.replaceAll("\\s+", "").length() >= minSize;
	}
	
	public boolean validEmail() {
		Matcher matcher = null;
		if( this.email == null || this.email.replaceAll("\\s+", "").equals("") )
			return false;
		else
			matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(this.email);
		return matcher.find();
	}
	
	public boolean validPhoneNr() {
		Matcher matcher = null;
		String phoneNrTemp = this.phoneNr;
		phoneNrTemp = phoneNrTemp.replaceAll("\\D", "");
		if( this.phoneNr == null )
			return false;
		else
			matcher = VALID_PHONE_NR_REGEX.matcher(phoneNrTemp);
		return matcher.find() && phoneNrTemp.length() == 9;
	}
	
	public boolean validAddress(long minSize) {
		if( this.address == null || this.address.replaceAll("\\s+", "").equals("") )
			return false;
		else
			return this.address.replaceAll("\\s+", "").length() >= minSize;
	}
		
}
