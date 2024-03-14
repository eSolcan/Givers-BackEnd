package giversapdc.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;

public class UserData {

	private static final Pattern VALID_EMAIL_ADDRESS_REGEX = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,3}$", Pattern.CASE_INSENSITIVE);
	private static final Pattern VALID_PHONE_NR_REGEX = Pattern.compile("(9[01236]\\d) ?(\\d{3}) ?(\\d{3})");
	
	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	
	private static final String M = "feminino";
	private static final String H = "masculino";
	private static final String O = "outro";
	private static final String NO_SAY = "prefiro não dizer";
	
	//We understand that this solution is sub-optimal, and that we should have put these on the database, 
	//but due to time constraints, and the not-so-useful nature of interests, we decided to go with a static list
	//private static final String[] INTERESTS = {"", "ambiente", "animais", "contraafome", "crianças", "idosos", "sem-abrigo", "refugiados", "outros"};
	
	public String username;		
	public String password;
	public String email;
	public String role;
	
	public String passwordConfirm;
	public String oldPassword;
	
	public String phoneNr;
	public String name;
	public long dateOfBirth;
	public String gender;
	public String nationality;
	public String address;
	public List<String> interests;
	public String description;
	public byte[] photo;
	
	public String code;
	
	public AuthToken at;
	
	public UserData() { }
		
	public Response validDataEdit(Transaction txn) {
		
		Key addressMinSizeKey = codeValueKeyFactory.newKey("addressminsize");
		long addressMinSize = txn.get(addressMinSizeKey).getLong("value");
		
		Key descriptionMinSizeKey = codeValueKeyFactory.newKey("descriptionminsize");
		long descriptionMinSize = txn.get(descriptionMinSizeKey).getLong("value");
		
		Key descriptionMaxSizeKey = codeValueKeyFactory.newKey("descriptionmaxsize");
		long descriptionMaxSize = txn.get(descriptionMaxSizeKey).getLong("value");
		
		if( !validPhoneNr() )
			return Response.status(Status.BAD_REQUEST).entity("Número de telefone deverá ter 9 algarismos e ser um válido.").build();
		else if( !validDateOfBirth() )
			return Response.status(Status.BAD_REQUEST).entity("Data de nascimento inválida").build();
		else if( !validGender() )
			return Response.status(Status.BAD_REQUEST).entity("Sexo inválido.").build();
		else if( !validNationality() )
			return Response.status(Status.BAD_REQUEST).entity("Nacionalidade inválida").build();
		else if( !validAddress(addressMinSize) )
			return Response.status(Status.BAD_REQUEST).entity("Morada inválida, deverá conter pelo menos " + addressMinSize + " caracteres.").build();
		else if( !validDescription(descriptionMinSize, descriptionMaxSize) )
			return Response.status(Status.BAD_REQUEST).entity("Descrição inválida, deverá conter entre " + descriptionMinSize + " e " + descriptionMaxSize + " caracteres.").build();
	
		return Response.ok().build();
	}
	
	public boolean validUsername() {
		return this.username != null && this.username.replaceAll("\\s+", "").length() >= 3;
	}
	
	public boolean validPassword() {
		return this.password != null && this.password.replaceAll("\\s+", "").length() >= 5;
	}
	
	public boolean validEmail() {
		Matcher matcher = null;
		if( this.email == null )
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
		else if( this.phoneNr.equals("") )
			return true;
		else
			matcher = VALID_PHONE_NR_REGEX.matcher(phoneNrTemp);
		return matcher.find() && phoneNrTemp.length() == 9;
	}
	
	public boolean validDateOfBirth() {
		if( this.dateOfBirth == 0 )
			return true;
		return this.dateOfBirth > 0 && this.dateOfBirth < System.currentTimeMillis();
	}
	
	//I understand that this is sub-optimal, but will have to do for now. Ideally would have these .equals values in database
	public boolean validGender() {
		return this.gender != null && 
				( this.gender.equals("") || 
					this.gender.equalsIgnoreCase(M) || 
					this.gender.equalsIgnoreCase(H) || 
					this.gender.equalsIgnoreCase(O) || 
					this.gender.equalsIgnoreCase(NO_SAY)
				);
	}
	
	public boolean validNationality() {
		if( this.nationality == null )
			return false;
		else if( this.nationality.equals("") )
			return true;
		else
			return this.nationality.replaceAll("\\s+", "").length() >= 2;
	}
	
	public boolean validAddress(long minSize) {
		if( this.address == null )
			return false;
		else if( this.address.equals("") )
			return true;
		else
			return this.address.replaceAll("\\s+", "").length() >= minSize;
	}
	
	public boolean validDescription(long minSize, long maxSize) {
		if( this.description == null )
			return false;
		else if( this.description.equals("") )
			return true;
		else
			return this.description.replaceAll("\\s+", "").length() >= minSize && this.description.replaceAll("\\s+", "").length() <= maxSize;
	}
		
}
