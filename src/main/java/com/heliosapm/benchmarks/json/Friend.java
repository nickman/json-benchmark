package com.heliosapm.benchmarks.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Friend {
	@JsonProperty("id")
	int id = -1;
	@JsonProperty("name")
	String name = null;
	
	public Friend() {
		// TODO Auto-generated constructor stub
	}
	
	public String toString() {
		return "Friend [" + id + ":" + name + "]";
	}

}
