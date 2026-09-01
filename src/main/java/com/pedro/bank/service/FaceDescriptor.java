package com.pedro.bank.service;

import com.pedro.bank.domain.FaceEnrollment;

import java.util.StringJoiner;

/**
 * Serialisation and comparison for face-api.js descriptors.
 *
 * <p>Two descriptors are considered the same person when the Euclidean distance
 * between them falls under a threshold. face-api.js documents 0.6 as its general
 * default; this application tightens it, trading a few more retries for fewer
 * false accepts.
 */
public final class FaceDescriptor {

    private FaceDescriptor() {
    }

    public static String serialize(double[] descriptor) {
        StringJoiner joiner = new StringJoiner(",");
        for (double value : descriptor) {
            joiner.add(Double.toString(value));
        }
        return joiner.toString();
    }

    public static double[] deserialize(String stored) {
        String[] parts = stored.split(",");
        double[] descriptor = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            descriptor[i] = Double.parseDouble(parts[i]);
        }
        return descriptor;
    }

    public static double distance(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Descriptors have different lengths");
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double difference = a[i] - b[i];
            sum += difference * difference;
        }
        return Math.sqrt(sum);
    }

    public static boolean isWellFormed(double[] descriptor) {
        if (descriptor == null || descriptor.length != FaceEnrollment.DESCRIPTOR_LENGTH) {
            return false;
        }
        for (double value : descriptor) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
