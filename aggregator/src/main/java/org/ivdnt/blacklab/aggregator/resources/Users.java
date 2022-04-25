import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import nl.inl.anw.ArtikelDatabase;
import nl.inl.anw.ArtikelException;
import nl.inl.anw.User;
import nl.inl.anw.User.Authority;
import nl.inl.anw.User.Role;
import nl.inl.anw.api.representation.UserObj;

@Path("/users")
public class Users {
    
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<UserObj> find(
            @DefaultValue("") @QueryParam("name") String nameSearch,
            @DefaultValue("both") @QueryParam("active") String active
            ) throws Exception {
        boolean mustBeActive = active.toLowerCase().matches("true|1|yes");
        boolean mustBeInactive = active.toLowerCase().matches("false|0|no");
        return ArtikelDatabase.getUsers().values().stream()
                .filter(user -> !mustBeActive || user.isActive())
                .filter(user -> !mustBeInactive || !user.isActive())
                .filter(user -> nameSearch.isEmpty() || (user.getFullName() + " " + user.getUserName() + " " + user.getInitials()).toLowerCase().contains(nameSearch.toLowerCase()))
                .map(user -> new UserObj(user))
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{userName}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public UserObj get(@PathParam("userName") String userName) throws Exception {
        return new UserObj(ArtikelDatabase.getUser(userName));
    }
    
    static class UserUpsertRequest {
        private String fullName = "";
        
        private String initials = "";
        
        private String role = "lexicologisch medewerker";
        
        private boolean active = true;

        public String getFullName() {
            return fullName;
        }

        public String getInitials() {
            return initials;
        }

        public String getRole() {
            return role;
        }

        public boolean isActive() {
            return active;
        }
    }

    /**
     * Create/update user
     * 
     * @param userName user name 
     * @param req request info
     * @return success or failure response
     * @throws ArtikelException
     */
    @PUT
    @Path("/{userName}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response update(
            @PathParam("userName") String userName,
            UserUpsertRequest req) throws ArtikelException {
        try (ArtikelDatabase db = ArtikelDatabase.getConnection()) {
            db.putUser(new User(0, userName, req.getFullName(), Authority.NORMAL, Role.fromName(req.getRole()), !req.isActive(), req.getInitials()));
            return Response.status(Response.Status.CREATED)
                .entity(new ResponseMessage("User " + userName + " created or updated"))
                .build();
            
        } catch (Exception e) {
            throw new ArtikelException("Error inserting/updating user", e);
        }
    }
}
