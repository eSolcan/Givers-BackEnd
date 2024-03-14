package giversapdc.util;

import java.util.List;

public class Pair {

	public List<?> list;
	public String cursor;
	
	public String text;
	public String link;
	
	public Pair(List<?> lt, String st) {
		this.list = lt;
		this.cursor = st;
	}
	
	public Pair(String text, String link) {
		this.text = text;
		this.link = link;
	}
}
