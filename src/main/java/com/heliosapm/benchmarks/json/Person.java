/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package com.heliosapm.benchmarks.json;

import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * <p>Title: Person</p>
 * <p>Description: The sample unmarshalling pojo tha JSON will unmarshall into instances of</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.benchmarks.json.Person</code></p>
 * <p>Sample Data:</p>
 * <pre>
  {
    "_id": "568fa582a22061cea69fb06c",
    "index": 0,
    "guid": "17e454c9-a6b7-4276-9ffe-7d7315c98843",
    "isActive": true,
    "balance": "$2,809.46",
    "picture": "http://placehold.it/32x32",
    "age": 39,
    "eyeColor": "brown",
    "name": "Noemi Richards",
    "gender": "female",
    "company": "FISHLAND",
    "email": "noemirichards@fishland.com",
    "phone": "+1 (845) 516-3620",
    "address": "787 Amity Street, Chautauqua, Guam, 9827",
    "about": "Occaecat cillum adipisicing aliquip dolor eu sint. Cupidatat qui mollit consectetur culpa deserunt quis nulla. Aute occaecat mollit labore cillum. Commodo nulla est nulla voluptate exercitation occaecat nisi sit. Aliqua do cillum consequat sunt nulla veniam ut id. Excepteur sunt id pariatur esse incididunt deserunt non et. Officia voluptate commodo nostrud deserunt aliquip ea.\r\n",
    "registered": "2014-12-21T04:48:24 +05:00",
    "latitude": 79.218987,
    "longitude": -170.255512,
    "tags": [
      "aliqua",
      "laborum",
      "ad",
      "ea",
      "tempor",
      "nostrud",
      "duis"
    ],
    "friends": [
      {
        "id": 0,
        "name": "Miller Arnold"
      },
      {
        "id": 1,
        "name": "Lydia Owen"
      },
      {
        "id": 2,
        "name": "Courtney Sawyer"
      }
    ],
    "greeting": "Hello, Noemi Richards! You have 1 unread messages.",
    "favoriteFruit": "banana"
  }
 * </pre>
 */

public class Person implements java.io.Serializable {
	@JsonProperty("_id")
	String id = null;
	@JsonProperty("index")
	long index = -1;
	@JsonProperty("guid")
	String guid = null;
	@JsonProperty("isActive")
	boolean active = false;
//	@JsonProperty("balance")
//	@JsonFormat(shape=Shape.NUMBER_FLOAT, pattern="$#,##0.0#")
//	float balance = -1f;
	@JsonProperty("picture")
	String picture = null;
	@JsonProperty("age")
	int age = -1;
	@JsonProperty("eyeColor")
	String eyeColor = null;
	@JsonProperty("name")
	String name = null;
	@JsonProperty("gender")
	String gender = null;
	@JsonProperty("company")
	String company = null;
	@JsonProperty("email")
	String email = null;
	@JsonProperty("phone")
	String phone = null;
	@JsonProperty("address")
	String address = null;
	@JsonProperty("registered")
	@JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
	Date registered = null;
	@JsonProperty("latitude")
	float latitude = -1f;
	@JsonProperty("longitude")
	float longitude = -1f;
	@JsonProperty("tags")
	Set<String> tags = null;
	@JsonProperty("friends")
	Friend[] friends = null;
	@JsonProperty("greeting")
	String greeting = null;
	@JsonProperty("favoriteFruit")
	String favoriteFruit = null;
	
	/**
	 * Creates a new Person
	 */
	public Person() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Person [id=");
		builder.append(id);
		builder.append(", index=");
		builder.append(index);
		builder.append(", guid=");
		builder.append(guid);
		builder.append(", active=");
		builder.append(active);
		builder.append(", picture=");
		builder.append(picture);
		builder.append(", age=");
		builder.append(age);
		builder.append(", eyeColor=");
		builder.append(eyeColor);
		builder.append(", name=");
		builder.append(name);
		builder.append(", gender=");
		builder.append(gender);
		builder.append(", company=");
		builder.append(company);
		builder.append(", email=");
		builder.append(email);
		builder.append(", phone=");
		builder.append(phone);
		builder.append(", address=");
		builder.append(address);
		builder.append(", registered=");
		builder.append(registered);
		builder.append(", latitude=");
		builder.append(latitude);
		builder.append(", longitude=");
		builder.append(longitude);
		builder.append(", tags=");
		builder.append(tags);
		builder.append(", friends=");
		builder.append(Arrays.toString(friends));
		builder.append(", greeting=");
		builder.append(greeting);
		builder.append(", favoriteFruit=");
		builder.append(favoriteFruit);
		builder.append("]");
		return builder.toString();
	}

}
