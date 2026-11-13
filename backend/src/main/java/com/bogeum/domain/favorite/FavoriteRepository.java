package com.bogeum.domain.favorite;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bogeum.domain.favorite.model.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

	// 이미 담은 매물인지 본다. 같은 매물은 다시 저장하지 않는다 (FAVORITE_001)
	boolean existsByUser_UserIdAndProperty_PropertyId(Long userId, Long propertyId);
}
