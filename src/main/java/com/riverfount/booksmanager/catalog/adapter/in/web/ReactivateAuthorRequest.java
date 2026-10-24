package com.riverfount.booksmanager.catalog.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** The optional body of the reactivation of an author (RN19). Without a body, a homonym is not confirmed. */
record ReactivateAuthorRequest(
        @Schema(description = "Confirma que o autor é um homônimo de um autor ativo: sem isso, reativar um autor cujo "
                + "nome outro autor ativo já tem é recusado (422, com o `existingAuthorId`). Opcional; o padrão é "
                + "falso.", example = "false")
        Boolean allowDuplicate) {
}
