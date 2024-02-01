// package org.acme;

// import java.util.Map;
// import java.util.concurrent.ConcurrentHashMap;

// import org.jboss.logging.Logger;

// import jakarta.enterprise.context.ApplicationScoped;
// import jakarta.websocket.OnClose;
// import jakarta.websocket.OnError;
// import jakarta.websocket.OnMessage;
// import jakarta.websocket.OnOpen;
// import jakarta.websocket.Session;
// import jakarta.websocket.server.PathParam;
// import jakarta.websocket.server.ServerEndpoint;

// @ServerEndpoint("/chat/{username}")
// @ApplicationScoped
// public class ChatSocket {
//     private static final Logger LOG = Logger.getLogger(ChatSocket.class);

//     Map<String, Session> sessions = new ConcurrentHashMap<>();

//     @OnOpen
//     public void onOpen(Session session, @PathParam("username") String username) {
//         broadcast("User " + username + " joined");
//         sessions.put(username, session);
//         LOG.info(username + " connected");
//     }

//     @OnClose
//     public void onClose(Session session, @PathParam("username") String username) {
//         sessions.remove(username, session);
//         broadcast("User " + username + " left");
//     }

//     @OnError
//     public void onError(Session session, @PathParam("username") String username, Throwable throwable) {
//         sessions.remove(username);
//         LOG.error("onError", throwable);
//         broadcast("User " + username + " left on error: " + throwable);
//     }

//     @OnMessage
//     public void onMessage(String message, @PathParam("username") String username) {
//         broadcast(username + ": " + message);
//     }

//     private void broadcast(String message) {
//         sessions.values().forEach(s -> {
//             s.getAsyncRemote().sendObject(message, result -> {
//                 if (result.getException() != null) {
//                     System.out.println("Unable to send message: " + result.getException());
//                 }
//             });
//         });
//     }
// }
package org.acme;

import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/chat/{username}")
@ApplicationScoped
public class ChatSocket {
    private static final Logger LOG = Logger.getLogger(ChatSocket.class);

    private static final String GROUP_PREFIX = "@";
    private static final String PRIVATE_PREFIX = "@@"; // You can customize the prefix for private messages

    Map<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("username") String username) {
        broadcast("User " + username + " joined");
        sessions.put(username, session);
        LOG.info(username + " connected");
    }

    @OnClose
    public void onClose(Session session, @PathParam("username") String username) {
        sessions.remove(username, session);
        broadcast("User " + username + " left");
    }

    @OnError
    public void onError(Session session, @PathParam("username") String username, Throwable throwable) {
        sessions.remove(username);
        LOG.error("onError", throwable);
        broadcast("User " + username + " left on error: " + throwable);
    }

    @OnMessage
    public void onMessage(String jsonMessage, @PathParam("username") String senderUsername) {
        try {
            // Parse the JSON message
            JsonObject jsonObject = Json.createReader(new StringReader(jsonMessage)).readObject();
            String content = jsonObject.getString("content", "");
            String recipient = jsonObject.getString("recipient", "");

            // Check if the recipient field is empty for broadcast
            if (recipient.isEmpty()) {
                broadcast(senderUsername + ": " + content);
            } else {
                // Check if the recipient is a group or an individual user
                if (recipient.startsWith(GROUP_PREFIX)) {
                    sendToGroup(content, senderUsername, recipient.substring(GROUP_PREFIX.length()));
                } else {
                    sendPrivateMessage(content, senderUsername, recipient);
                }
            }
        } catch (JsonException e) {
            LOG.error("Error parsing JSON message", e);
        }
    }

    private void sendToGroup(String content, String senderUsername, String groupName) {
        sessions.values().stream()
                .filter(s -> groupName.equals(s.getUserProperties().get("group")))
                .forEach(s -> {
                    s.getAsyncRemote().sendObject(senderUsername + " (group " + groupName + "): " + content, result -> {
                        if (result.getException() != null) {
                            System.out.println("Unable to send group message: " + result.getException());
                        }
                    });
                });
    }

    private void sendPrivateMessage(String content, String senderUsername, String recipient) {
        Session recipientSession = sessions.get(recipient);
        if (recipientSession != null) {
            recipientSession.getAsyncRemote().sendObject(senderUsername + " (private): " + content, result -> {
                if (result.getException() != null) {
                    System.out.println("Unable to send private message: " + result.getException());
                }
            });
        }
    }

    private void broadcast(String message) {
        sessions.values().forEach(s -> {
            s.getAsyncRemote().sendObject(message, result -> {
                if (result.getException() != null) {
                    System.out.println("Unable to send message: " + result.getException());
                }
            });
        });
    }
}
