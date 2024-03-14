package giversapdc.util;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;

public class SuggestionReportData {

	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	
	public String type;	//Indicates if it's a suggestion or a report
	public String subtype;
	public String text;
	public AuthToken at;
	public String name;
	
	
	public SuggestionReportData() { }
	
	
	public Response validData(Transaction txn) { 
		Key textMinSizeKey = codeValueKeyFactory.newKey("commentminsize");
		long textMinSize = txn.get(textMinSizeKey).getLong("value");
		
		Key textMaxSizeKey = codeValueKeyFactory.newKey("commentmaxsize");
		long textMaxSize = txn.get(textMaxSizeKey).getLong("value");
		
		if( !validText(textMinSize, textMaxSize) )		
			return Response.status(Status.BAD_REQUEST).entity("Texto deve conter entre " + textMinSize +" e " + textMaxSize + " caracteres.").build();
		else if( !validType() )
			return Response.status(Status.BAD_REQUEST).entity("Tipo inválido.").build();
		else if( !validSubType() )
			return Response.status(Status.BAD_REQUEST).entity("Título não pode ser vazio.").build();
		else
			return Response.ok().build();
	}
		
	public boolean validText(long minSize, long maxSize) {
		return this.text != null && this.text.replaceAll("\\s+", "").length() >= minSize && this.text.replaceAll("\\s+", "").length() <= maxSize;
	}
	
	public boolean validType() {
		if( this.type == null || this.type.replaceAll("\\s+", "").equals("") )
			return false;
		else
			return true;
	}
	
	public boolean validSubType() {
		if( this.subtype == null || this.subtype.replaceAll("\\s+", "").equals("") )
			return false;
		else
			return true;
	}
	

	
}
