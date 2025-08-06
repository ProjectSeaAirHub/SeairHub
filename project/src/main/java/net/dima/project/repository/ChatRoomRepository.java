package net.dima.project.repository;

import net.dima.project.entity.ChatRoom;
import net.dima.project.entity.OfferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByOffer(OfferEntity offer);
    
    // [이 메서드를 추가해주세요]
    @Query("SELECT cr FROM ChatRoom cr JOIN cr.participants p WHERE p.user.userSeq = :userSeq ORDER BY cr.createdAt DESC")
    List<ChatRoom> findAllByUserSeq(@Param("userSeq") Integer userSeq);
}