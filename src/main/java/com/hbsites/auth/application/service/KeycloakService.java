package com.hbsites.auth.application.service;

import com.hbsites.commons.domain.dto.UserDTO;
import com.hbsites.commons.infrastructure.messages.incoming.UserInfoRequestMessage;
import com.hbsites.commons.infrastructure.messages.outgoing.UserInfoResponseMessage;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.java.Log;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Log
public class KeycloakService {

    @Inject
    Keycloak keycloak;

    @ActivateRequestContext
    public Uni<UserInfoResponseMessage> getUsersById(UserInfoRequestMessage request) {
        return Uni.createFrom().item(keycloak.tokenManager().getAccessToken()).onItem().transformToUni(token -> getFromKeycloak(request.getUserIds()).onItem()
                .transform(userRepresentations ->
                        UserInfoResponseMessage.fromRequest()
                                .request(request)
                                .userInfos(userRepresentations.stream().map(userRepresentation ->
                                        UserDTO.builder()
                                                .uuid(UUID.fromString(userRepresentation.getId()))
                                                .userName(userRepresentation.getUsername())
                                                .firstName(userRepresentation.getFirstName())
                                                .lastName(userRepresentation.getLastName())
                                                .build()
                                ).toList()).build()
                ).onTermination().invoke(() -> keycloak.tokenManager().logout()));
    }

    private Uni<List<UserRepresentation>> getFromKeycloak(List<UUID> uuids) {
        List<UserRepresentation> users = new ArrayList<>();
        UsersResource resource = keycloak.realm("HBsites").users();
        for (UUID uuid : uuids) {
            UserResource userResource = resource.get(uuid.toString());
            UserRepresentation  representation;
            try {
                representation = userResource != null ? userResource.toRepresentation() : null;
                if (representation != null) {
                    users.add(representation);
                }
            } catch(ClientWebApplicationException e) {
                if (Response.Status.NOT_FOUND.equals(e.getResponse().getStatusInfo().toEnum())) {
                    log.warning("[AUTH-USER] - User with id '%s' not found!".formatted(uuid.toString()));
                } else {
                    log.severe("[AUTH-USER] - %s".formatted(e.getLocalizedMessage()));
                }
            }
        }
        return Uni.createFrom().item(users);
    }
}
