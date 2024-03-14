package giversapdc.util;

import java.util.UUID;


public class AuthToken {

	public String username;
	public String tokenID;
	public String role;
	public long creationDate;
	public long expirationDate;
	
	//public static final long EXPIRATION_TIME = 1000*60*60*2; 	//2h in milliseconds
	
	public AuthToken() { } 	
	
	public AuthToken(String tokenId, String username, String role, long expirationTime) {
		this.username = username;
		this.tokenID = tokenId;
		this.role = role;
		this.creationDate = System.currentTimeMillis();
		this.expirationDate = this.creationDate + expirationTime;
	}
	
	public AuthToken(String username, String role, long expirationTime) {
		this.username = username;
		this.tokenID = UUID.randomUUID().toString();
		this.role = role;
		this.creationDate = System.currentTimeMillis();
		this.expirationDate = this.creationDate + expirationTime;
	}
	
	
	
}
