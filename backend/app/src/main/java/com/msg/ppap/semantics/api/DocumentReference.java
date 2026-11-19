package com.msg.ppap.semantics.api;

/**
 * Metadata of a document referenced by a payload; the content is identified by its SHA-256.
 *
 * @param fileName original file name
 * @param mediaType detected media type
 * @param size size in bytes
 * @param sha256 lower-case hex SHA-256 of the content
 */
public record DocumentReference(String fileName, String mediaType, long size, String sha256) {}
