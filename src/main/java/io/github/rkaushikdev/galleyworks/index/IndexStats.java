package io.github.rkaushikdev.galleyworks.index;

/**
 * A summary of an index an operator can read without dumping it.
 *
 * @param instruments distinct instruments with at least one segment
 * @param segments indexed segments
 * @param buckets distinct LSH band keys
 * @param postings distinct winnowed fingerprints
 * @param digest the engine digest the index was built under
 */
public record IndexStats(int instruments, int segments, int buckets, int postings, String digest) {}
