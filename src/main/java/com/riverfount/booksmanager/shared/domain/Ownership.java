package com.riverfount.booksmanager.shared.domain;

/**
 * What a reader reaches is what is theirs (Phase 6, RNF08). A resource that belongs to another reader is answered
 * exactly like one that does not exist (404), so the answer never tells whether it exists. This is the single place
 * of the rule: the use cases of the reader ask it, or better, look the resource up by the id and its owner together.
 * The id of the caller always comes from the access token, never from the request.
 */
public final class Ownership {

    private Ownership() {
    }

    /** Whether the resource belongs to the caller. An unknown owner or caller never matches. */
    public static boolean isOwner(Long ownerReaderId, Long callerReaderId) {
        return ownerReaderId != null && ownerReaderId.equals(callerReaderId);
    }

    /**
     * @param notFoundMessage the same message the use case gives when the resource does not exist, such as
     *        "reserva não encontrada: 7", so the two cases cannot be told apart
     * @throws ResourceNotFoundException if the resource does not belong to the caller
     */
    public static void requireOwner(Long ownerReaderId, Long callerReaderId, String notFoundMessage) {
        if (!isOwner(ownerReaderId, callerReaderId)) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
    }
}
