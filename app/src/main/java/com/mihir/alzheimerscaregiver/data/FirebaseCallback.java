package com.mihir.alzheimerscaregiver.data;

/**
 * Common Firebase callback interface for all repositories
 */
public interface FirebaseCallback<T> {
    void onSuccess(T result);
    void onError(String error);
}