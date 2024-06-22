package com.cookiebuild.cookiedough.dao;

import java.util.UUID;

public interface GenericDAO<T> {
    void save(T entity);
    T findById(UUID id);
    void delete(T entity);
    void update(T entity);
}