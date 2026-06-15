package com.hotevent.repository;

import com.hotevent.entity.EventTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventTranslationRepository extends JpaRepository<EventTranslation, Long> {

    Optional<EventTranslation> findByEventIdAndLanguage(Long eventId, String language);

    List<EventTranslation> findByEventId(Long eventId);

    List<EventTranslation> findByLanguage(String language);

    List<EventTranslation> findByEventIdIn(List<Long> eventIds);

    void deleteByEventIdAndLanguage(Long eventId, String language);

    boolean existsByEventIdAndLanguage(Long eventId, String language);
}
