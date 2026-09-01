package com.cookiebuild.cookiedough.dao;

import java.util.UUID;

import com.cookiebuild.cookiedough.utils.HibernateUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

public class GenericDAOImpl<T> implements GenericDAO<T> {
    private final Class<T> entityClass;

    public GenericDAOImpl(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public void save(T entity) {
        EntityManager em = HibernateUtil.createEntityManager();
        EntityTransaction transaction = null;
        try {
            transaction = em.getTransaction();
            transaction.begin();
            em.persist(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            throw new IllegalStateException("Failed to save " + entityClass.getSimpleName(), e);
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    @Override
    public T findById(UUID id) {
        EntityManager em = HibernateUtil.createEntityManager();
        try {
            return em.find(entityClass, id);
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    @Override
    public void delete(T entity) {
        EntityManager em = HibernateUtil.createEntityManager();
        EntityTransaction transaction = null;
        try {
            transaction = em.getTransaction();
            transaction.begin();
            em.remove(em.contains(entity) ? entity : em.merge(entity));
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            throw new IllegalStateException("Failed to delete " + entityClass.getSimpleName(), e);
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    @Override
    public void update(T entity) {
        EntityManager em = HibernateUtil.createEntityManager();
        EntityTransaction transaction = null;
        try {
            transaction = em.getTransaction();
            transaction.begin();
            em.merge(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            throw new IllegalStateException("Failed to update " + entityClass.getSimpleName(), e);
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }
}
