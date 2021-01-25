package us.livebythecode.rest.services.resources;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.google.common.collect.Sets;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Path("/squid-configuration/whitelist-domains")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WhitelistResource {

    @ConfigProperty(name = "squid.configuration.whitelistcount")
    int whitelistCount;

    @ConfigProperty(name = "squid.configuration.basepath")
    String configBasePath;

    @ConfigProperty(name = "squid.reloadcommand")
    String squidReloadCommand;

    private List<TreeSet<String>> getDomainsNameSets() throws IOException {
        List<TreeSet<String>> domainNameSetList = new ArrayList<>();
        for (int i = 0; i < whitelistCount; i++) {
            domainNameSetList.add(Sets.newTreeSet(Files.readAllLines(FileSystems.getDefault().getPath(configBasePath + "/whitelist"+(i+1)+".acl"), StandardCharsets.UTF_8)));
        }
        return domainNameSetList;
    }

    @GET
    @Path("{listID}")
    public Set<String> list(@PathParam("listID") int listID) throws IOException {
        return getDomainsNameSets().get(listID);
    }

    @POST
    @Path("{listID}")
    public Set<String> add(@PathParam("listID") int listID, @QueryParam("domainName") String domainName) throws IOException, InterruptedException {
        Set<String> updateSet = getDomainsNameSets().get(listID);
        if (isAlphaNumeric(domainName)) {
            updateSet.add(formatDomainName(domainName));
            writeFile(listID, updateSet);
        }
        return updateSet;
    }

    @DELETE
    @Path("{listID}")
    public Set<String> delete(@PathParam("listID") int listID, @QueryParam("domainName") String domainName) throws IOException, InterruptedException {
        Set<String> updateSet = getDomainsNameSets().get(listID);
        updateSet.removeIf(existingDomainName -> existingDomainName.contentEquals(formatDomainName(domainName)));
        int returnCode = writeFile(listID, updateSet);
        //TODO: handle return code
        return updateSet;
    }

    private String formatDomainName(String inputName) {
        String returnName = inputName != null ? inputName : "";
        if (returnName.indexOf(".") != 0) {
            returnName = "." + returnName;
        }
        return returnName.toLowerCase();
    }

    private boolean isAlphaNumeric(String s) {
        return s.matches("^[a-zA-Z0-9.]*$");
    }

    private int writeFile(int listID, Set<String> domainNameSet) throws IOException, InterruptedException {
        Files.write(FileSystems.getDefault().getPath(configBasePath + "/whitelist"+(listID+1)+".acl"), domainNameSet);
        return reloadSquidConfig();
    }
    
    private int reloadSquidConfig() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("sh", "-c", squidReloadCommand);
        Process process = builder.start();
        return process.waitFor();
    }
}
