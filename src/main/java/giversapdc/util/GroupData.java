package giversapdc.util;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class GroupData {

	public String name;
	public int capacity;
	public String description;
	
	public String password;
	public String passwordConfirm;
	
	public AuthToken at;
	
	//public String photo;
	public byte[] photo;
	
	public String startCursorString;
	
	public GroupData() { }
	
	public Response validDataRegister() {
		if( !validName() )
			return Response.status(Status.BAD_REQUEST).entity("Nome deve conter pelo menos 3 caracteres.").build();
		else if( !validCapacity() )
			return Response.status(Status.BAD_REQUEST).entity("Capacidade deve ser pelo menos 1.").build();
		else if( !validDescription() )
			return Response.status(Status.BAD_REQUEST).entity("Descrição deve conter pelo menos 3 caracteres.").build();
		else
			return Response.ok().build();
	}
	
	public Response validDataEdit() {
		if( !this.name.equals("") && !validName() )
			return Response.status(Status.BAD_REQUEST).entity("Nome deve conter pelo menos 3 caracteres.").build();
		else if( !validCapacity() )
			return Response.status(Status.BAD_REQUEST).entity("Capacidade deve ser pelo menos 1.").build();
		else if( !this.description.equals("") && !validDescription() )
			return Response.status(Status.BAD_REQUEST).entity("Descrição deve conter pelo menos 3 caracteres.").build();
		else
			return Response.ok().build();
	}
	
	public boolean validName() {
		return this.name != null && this.name.replaceAll("\\s+", "").length() >= 3;
	}
	
	public boolean validCapacity() {
		return capacity > 1;
	}
	
	public boolean validDescription() {
		return this.description != null && this.description.replaceAll("\\s+", "").length() >= 3;
	}
	
}
