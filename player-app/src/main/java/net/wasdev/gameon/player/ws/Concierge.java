/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package net.wasdev.gameon.player.ws;

import java.util.List;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedThreadFactory;
import javax.enterprise.context.ApplicationScoped;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

/**
 *
 */
@ApplicationScoped
public class Concierge {

	/** CDI injection of Java EE7 Managed thread factory */
	@Resource
	protected ManagedThreadFactory threadFactory;

	private String conciergeLocation;

	Client client;
	WebTarget root;

	@PostConstruct
	public void initClient() {
		try {
			this.conciergeLocation = (String) new InitialContext().lookup("conciergeUrl");
		} catch (NamingException e) {
		}
		this.client = ClientBuilder.newClient();
		this.root = this.client.target(conciergeLocation);

		Log.log(Level.FINER, this, "Concierge initialized with {0}", conciergeLocation);
	}

	public Room checkin(PlayerSession playerSession, Room currentRoom, String roomId) {
		if ( roomId == null || roomId.isEmpty() || Constants.FIRST_ROOM.equals(roomId) ) {
			// NEWBIE!!
			return new FirstRoom();
		}

		if ( currentRoom != null ) {
			if ( currentRoom.getId().equals(roomId)) {
				// SESSION RESUME!! WOO!!
				return currentRoom;
			} else {
				// The player moved rooms somewhere along the way
				// we need to make sure we detach the old session
				currentRoom.unsubscribe(playerSession);
			}
		}

		// Make a new room;
		RoomEndpointList endpointList = getRoomEndpoints(roomId);
		RemoteRoom room = new RemoteRoom(roomId, endpointList.getEndpoints(), threadFactory);

		// Create a new room
		return room;
	}


	/**
	 * @param currentRoom
	 * @return
	 */
	public Room changeRooms(Room currentRoom, String exit) {
		RoomEndpointList roomEndpoints = null;

		if ( exit == null ) {
			// SOS!! randomly grab a new room (start over with starting rooms)
			roomEndpoints = getRoomEndpoints();
		} else {
			roomEndpoints = getRoomEndpoints(currentRoom.getId(), exit);
		}

		return new RemoteRoom(roomEndpoints.getRoomId(), roomEndpoints.getEndpoints(), threadFactory);
	}

	public RoomEndpointList getRoomEndpoints() {
		WebTarget target = this.root.path("startingLocation");
		Log.log(Level.FINER, this, "making requestion to {0} for starting rooms", target.toString());
		RoomEndpointList result = target.request(MediaType.APPLICATION_JSON).get(RoomEndpointList.class);

		return result;
	}

	public RoomEndpointList getRoomEndpoints(String roomId) {
		WebTarget target = this.root.path("rooms/{roomId}").resolveTemplate("roomId", roomId);
		Log.log(Level.FINER, this, "making requestion to {0} for room", target.toString());
		RoomEndpointList result = target.request(MediaType.APPLICATION_JSON).get(RoomEndpointList.class);

		return result;
	}

	public RoomEndpointList getRoomEndpoints(String roomId, String exit) {
		WebTarget target = this.root.path("rooms/{roomId}/{exit}").resolveTemplate("roomId", roomId).resolveTemplate("exit", exit);
		Log.log(Level.FINER, this, "making requestion to {0} for list of exit", target.toString());
		RoomEndpointList result = target.request(MediaType.APPLICATION_JSON).get(RoomEndpointList.class);

		return result;
	}

	static class RoomList {
		List<RoomEndpointList> rooms;

		public List<RoomEndpointList> getRooms() {
			return rooms;
		}
	}

	static class RoomEndpointList {
		String roomId;
		List<String> endpoints;

		/**
		 * @return the roomId
		 */
		public String getRoomId() {
			return roomId;
		}

		/**
		 * @param roomId the roomId to set
		 */
		public void setRoomId(String roomId) {
			this.roomId = roomId;
		}

		/**
		 * @return the endpoints
		 */
		public List<String> getEndpoints() {
			return endpoints;
		}

		/**
		 * @param endpoints the endpoints to set
		 */
		public void setEndpoints(List<String> endpoints) {
			this.endpoints = endpoints;
		}
	}
}
