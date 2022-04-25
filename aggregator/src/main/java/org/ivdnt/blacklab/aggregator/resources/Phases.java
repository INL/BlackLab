import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import nl.inl.anw.ArtikelDatabase;
import nl.inl.anw.api.representation.Phase;

@Path("/phases")
public class Phases {
    
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Phase> list() throws Exception {
        return ArtikelDatabase.getArtikelFases().stream()
                .map(f -> new Phase(f))
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Phase get(@PathParam("id") int id) throws Exception {
        return new Phase(ArtikelDatabase.getArtikelFase(id));
    }
}
