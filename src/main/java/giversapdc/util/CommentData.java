package giversapdc.util;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;

public class CommentData {

	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	
	public String eventName;	
	public String commentText;
	public String commentId;
	public byte[] photo;
	
	public AuthToken at;
	
	public CommentData() {}
	
	public Response validData(Transaction txn) {
		Key commentMinSizeKey = codeValueKeyFactory.newKey("commentminsize");
		long commentMinSize = txn.get(commentMinSizeKey).getLong("value");
		
		Key commentMaxSizeKey = codeValueKeyFactory.newKey("commentmaxsize");
		long commentMaxSize = txn.get(commentMaxSizeKey).getLong("value");
		
		if( !validName() )
			return Response.status(Status.BAD_REQUEST).entity("Nome do evento deve conter pelo menos 1 caractere.").build();
		else if( !validComment(commentMinSize, commentMaxSize) )
			return Response.status(Status.BAD_REQUEST).entity("Comentário deve conter entre " + commentMinSize + " e " + commentMaxSize + " caracteres.").build();
		else 
			return Response.ok().build();
	}
	
	public boolean validName() {
		return this.eventName != null && this.eventName.replaceAll("\\s+", "").length() >= 1;
	}
	
	public boolean validComment(long minSize, long maxSize) {
		return this.commentText != null && this.commentText.replaceAll("\\s+", "").length() >= minSize && this.commentText.replaceAll("\\s+", "").length() <= maxSize;
	}
}
