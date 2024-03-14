package giversapdc.util;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;

public class ShopItemData {

	private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
	private KeyFactory codeValueKeyFactory = datastore.newKeyFactory().setKind("CodeValue");
	
	public String providerName;
	public String itemName;
	public String description;
	public int pricePer;
	public int quantity;
	public byte[] photo;
	
	public AuthToken at;
	
	public ShopItemData() { }
	
	public Response validDataRegister(Transaction txn) {
		Key minNameShopKey = codeValueKeyFactory.newKey("minnameshop");
		long minNameShop = txn.get(minNameShopKey).getLong("value");
		
		Key minNameShopProviderKey = codeValueKeyFactory.newKey("minnameshopprovider");
		long minNameShopProvider = txn.get(minNameShopProviderKey).getLong("value");
		
		Key minDescriptionKey = codeValueKeyFactory.newKey("descriptionminsize");
		long minDescription = txn.get(minDescriptionKey).getLong("value");
		
		Key maxDescriptionKey = codeValueKeyFactory.newKey("descriptionmaxsize");
		long maxDescription = txn.get(maxDescriptionKey).getLong("value");
		

		if( !validName(minNameShop) )
			return Response.status(Status.BAD_REQUEST).entity("Nome do item deve conter pelo menos " + minNameShop + " caracteres.").build();
		else if( !validProviderName(minNameShopProvider) )
			return Response.status(Status.BAD_REQUEST).entity("Nome do fornecedor deve conter pelo menos " + minNameShopProvider + " caracteres.").build();
		else if( !validPrice() )
			return Response.status(Status.BAD_REQUEST).entity("Preço por unidade deve ser maior que zero.").build();
		else if( !validQuantity() )
			return Response.status(Status.BAD_REQUEST).entity("Quantidade deve ser maior ou igual a zero.").build();
		else if( !validDescription(minDescription, maxDescription) )
			return Response.status(Status.BAD_REQUEST).entity("Descrição deverá conter entre " + minDescription + " e " + maxDescription + " caracteres.").build();			
		else
			return Response.ok().build();
	}
	
	public Response validDataEdit(Transaction txn) {
		Key minDescriptionKey = codeValueKeyFactory.newKey("descriptionminsize");
		long minDescription = txn.get(minDescriptionKey).getLong("value");
		
		Key maxDescriptionKey = codeValueKeyFactory.newKey("descriptionmaxsize");
		long maxDescription = txn.get(maxDescriptionKey).getLong("value");
		
		if( !validDescription(minDescription, maxDescription) )
			return Response.status(Status.BAD_REQUEST).entity("Descrição deverá conter entre " + minDescription + " e " + maxDescription + " caracteres.").build();	
		else if( !validPrice() )
			return Response.status(Status.BAD_REQUEST).entity("Preço por unidade deve ser maior que zero.").build();
		else if( !validQuantity() )
			return Response.status(Status.BAD_REQUEST).entity("Quantidade deve ser maior ou igual a zero.").build();
		else
			return Response.ok().build();
	}
		
	public boolean validName(long minSize) {
		return this.itemName != null && this.itemName.replaceAll("\\s+", "").length() >= minSize;
	}
	
	public boolean validProviderName(long minSize) {
		return this.providerName != null && this.providerName.replaceAll("\\s+", "").length() >= minSize;
	}
	
	public boolean validPrice() {
		return this.pricePer > 0;
	}
	
	public boolean validQuantity() {
		return this.quantity >= 0;
	}
	
	public boolean validDescription(long minSize, long maxSize) {
		return this.description != null && this.description.length() >= minSize && this.description.length() <= maxSize;
	}
	
}
