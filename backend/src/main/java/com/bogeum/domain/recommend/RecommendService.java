package com.bogeum.domain.recommend;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bogeum.common.enums.PropertyStatus;
import com.bogeum.common.enums.Role;
import com.bogeum.domain.favorite.FavoriteRepository;
import com.bogeum.domain.favorite.model.Favorite;
import com.bogeum.domain.property.PropertyException;
import com.bogeum.domain.property.PropertyRepository;
import com.bogeum.domain.property.PropertyService;
import com.bogeum.domain.property.model.Property;
import com.bogeum.domain.property.model.PropertyDto;
import com.bogeum.domain.property.model.PropertyViewLog;
import com.bogeum.domain.recommend.model.PropertyCount;
import com.bogeum.domain.recommend.model.RecommendDto;
import com.bogeum.domain.recommend.model.SearchLog;
import com.bogeum.domain.recommend.model.SimilarBasis;
import com.bogeum.domain.user.UserRepository;
import com.bogeum.domain.user.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecommendService {

	// 매물 찾기는 입력하는 동안에도 검색해서, 이 시간 안에 이어진 검색은 하나로 본다 (RECOMMEND_001)
	private static final long SEARCH_MERGE_SECONDS = 10;
	// 같은 사람이 같은 매물을 이 시간 안에 다시 보면 조회 수를 늘리지 않는다 (RECOMMEND_001)
	private static final long VIEW_DEDUP_HOURS = 24;
	// 관심 지역과 인기 조회 수는 최근 이 기간의 기록으로 정한다
	private static final long SIGNAL_DAYS = 30;
	// 관심 신호의 무게. 담은 매물 > 검색한 지역 > 본 매물 순으로 관심이 크다고 본다
	private static final int FAVORITE_WEIGHT = 3;
	private static final int SEARCH_WEIGHT = 2;
	private static final int VIEW_WEIGHT = 1;
	// 인기 점수에서 관심 등록 한 번은 조회 세 번으로 친다
	private static final long FAVORITE_POPULARITY = 3;
	// 관심 지역·지하철역은 점수가 높은 것부터 이만큼만 쓴다
	private static final int MAX_INTERESTS = 3;
	// 인기 순위를 매길 후보 수. 최근 공개된 매물부터 가져온다
	private static final int CANDIDATE_LIMIT = 100;
	// 추천은 카드 줄이 맞는 12개까지 받는다
	private static final int MAX_SIZE = 12;
	// 비슷한 매물은 최근 본 매물·관심 매물 중 최근 것 이만큼을 기준으로 찾는다 (RECOMMEND_002)
	private static final int SIMILAR_BASE_LIMIT = 3;

	private final SearchLogRepository searchLogRepository;
	private final PropertyViewLogRepository propertyViewLogRepository;
	private final FavoriteRepository favoriteRepository;
	private final UserRepository userRepository;
	private final PropertyRepository propertyRepository;
	private final PropertyService propertyService;

	/**
	 * 매물 찾기에서 검색한 지역·지하철역을 남긴다 (RECOMMEND_001).
	 * 추천을 받는 사용자(USER)의 검색만 남긴다. 중개사의 업무 검색이 관심 지역에 섞이지 않게 한다.
	 * 바로 앞과 같은 검색은 건너뛰고, 10초 안에 이어서 고친 검색은 앞 검색을 덮어쓴다.
	 *
	 * @param isFirstPage 스크롤로 다음 목록을 부른 요청은 같은 검색이라 남기지 않는다
	 */
	@Transactional
	public void recordSearch(Long userId, String region, String subway, boolean isFirstPage) {
		String searchedRegion = trimToNull(region);
		String searchedSubway = trimToNull(subway);
		if (!isFirstPage || searchedRegion == null && searchedSubway == null) {
			return;
		}
		User user = findRecommendTarget(userId);
		if (user == null) {
			return;
		}
		LocalDateTime now = LocalDateTime.now();
		SearchLog last = searchLogRepository.findFirstByUser_UserIdOrderBySearchedAtDescSearchLogIdDesc(userId)
			.orElse(null);
		if (last != null && Objects.equals(last.getRegion(), searchedRegion)
				&& Objects.equals(last.getSubwayStation(), searchedSubway)) {
			return;
		}
		if (last != null && last.getSearchedAt().isAfter(now.minusSeconds(SEARCH_MERGE_SECONDS))) {
			last.overwrite(searchedRegion, searchedSubway, now);
			return;
		}
		searchLogRepository.save(SearchLog.builder().user(user).region(searchedRegion).subwayStation(searchedSubway)
			.searchedAt(now).build());
	}

	/**
	 * 공개 매물 상세를 본 기록을 남긴다. 관심 지역과 인기 매물 집계에 쓴다 (RECOMMEND_001).
	 * 사용자(USER)의 조회만, 같은 매물은 24시간에 한 번만 남긴다. 임대인이 본인 매물을 본 것은 인기에 넣지 않는다.
	 */
	@Transactional
	public void recordView(Long userId, Long propertyId) {
		User user = findRecommendTarget(userId);
		Property property = user == null ? null : propertyRepository.findById(propertyId).orElse(null);
		if (property == null || property.getLandlord().getUserId().equals(userId)) {
			return;
		}
		if (propertyViewLogRepository.existsByUser_UserIdAndProperty_PropertyIdAndViewedAtAfter(userId, propertyId,
				LocalDateTime.now().minusHours(VIEW_DEDUP_HOURS))) {
			return;
		}
		propertyViewLogRepository.save(PropertyViewLog.builder().user(user).property(property).build());
	}

	/**
	 * 사용자를 위한 추천 매물을 조회한다 (RECOMMEND_001).
	 * 최근 30일의 관심 매물·검색·조회로 관심 지역·지하철역을 정하고, 그 근처 공개 매물을 인기 순으로 준다.
	 * 인기는 최근 30일 조회 수 + 관심 수 × 3이다. 근처 매물이 모자라거나 기록이 없으면 전체 인기 매물로 채운다.
	 * 본인 매물과 이미 담은 매물은 빼서 새 매물을 보게 한다.
	 *
	 * @param size 1~12, 범위를 벗어나면 가장 가까운 값으로 맞춘다
	 */
	@Transactional(readOnly = true)
	public RecommendDto.RecommendListResponse readRecommendations(Long userId, int size) {
		int limit = clampSize(size);
		LocalDateTime since = LocalDateTime.now().minusDays(SIGNAL_DAYS);
		Map<String, Integer> regionScores = new HashMap<>();
		Map<String, Integer> stationScores = new HashMap<>();
		favoriteRepository.findAllByUser_UserIdAndCreatedAtAfter(userId, since).forEach(favorite ->
			addInterest(regionScores, stationScores, favorite.getProperty(), FAVORITE_WEIGHT));
		searchLogRepository.findAllByUser_UserIdAndSearchedAtAfter(userId, since).forEach(search -> {
			addScore(regionScores, search.getRegion(), SEARCH_WEIGHT);
			addScore(stationScores, search.getSubwayStation(), SEARCH_WEIGHT);
		});
		propertyViewLogRepository.findAllByUser_UserIdAndViewedAtAfter(userId, since).forEach(view ->
			addInterest(regionScores, stationScores, view.getProperty(), VIEW_WEIGHT));
		List<String> regions = topInterests(regionScores);
		List<String> stations = topInterests(stationScores);
		boolean isPersonalized = !regions.isEmpty() || !stations.isEmpty();

		Map<Long, Long> popularity = readPopularity(since);
		Set<Long> favoritedIds = new HashSet<>(favoriteRepository.findPropertyIdsByUserId(userId));
		Map<Long, Property> picked = new LinkedHashMap<>();
		if (isPersonalized) {
			pickPopular(picked, propertyService.readRecommendCandidates(regions, stations, userId, CANDIDATE_LIMIT),
				popularity, favoritedIds, limit);
		}
		if (picked.size() < limit) {
			pickPopular(picked, readGlobalCandidates(userId, popularity), popularity, favoritedIds, limit);
		}

		return RecommendDto.RecommendListResponse.builder()
			.properties(toItems(new ArrayList<>(picked.values())))
			.regions(regions)
			.stations(stations)
			.isPersonalized(isPersonalized)
			.build();
	}

	/**
	 * 매물 상세에서 보고 있는 매물과 비슷한 매물을 조회한다 (RECOMMEND_002).
	 * 임대 형식이 같은 공개 매물을 매물 종류·가격·면적·방 개수·시·구·지하철역으로 비교해 비슷한 순서로 준다.
	 * 본인 매물과 이미 담은 관심 매물은 뺀다.
	 *
	 * @throws PropertyException 없거나 공개 중이 아닌 매물인 경우. 상세도 공개 중인 매물만 열린다
	 */
	@Transactional(readOnly = true)
	public RecommendDto.SimilarListResponse readSimilarProperties(Long viewerId, Long propertyId, int size) {
		Property base = propertyRepository.findByPropertyIdAndPropertyStatus(propertyId, PropertyStatus.ACTIVE)
			.orElseThrow(PropertyException::propertyNotFound);
		Set<Long> excludedIds = new HashSet<>(favoriteRepository.findPropertyIdsByUserId(viewerId));
		return RecommendDto.SimilarListResponse.builder()
			.properties(toItems(pickSimilar(List.of(base), viewerId, excludedIds, size)))
			.build();
	}

	/**
	 * 최근 본 매물이나 담은 관심 매물과 비슷한 매물을 조회한다. 메인과 관심 매물 화면에서 쓴다 (RECOMMEND_002).
	 * 기준 매물은 최근 것 3개이고, 후보마다 가장 비슷한 기준 매물의 점수로 줄 세운다.
	 * 기준 매물 자신, 본인 매물, 이미 담은 관심 매물은 뺀다. 기준 매물이 없으면 빈 목록이라 화면이 추천 기준을 안내한다.
	 */
	@Transactional(readOnly = true)
	public RecommendDto.SimilarListResponse readSimilarByBasis(Long userId, SimilarBasis basis, int size) {
		List<Property> bases = basis == SimilarBasis.VIEW ? readRecentViewed(userId) : readRecentFavorites(userId);
		Set<Long> excludedIds = new HashSet<>(favoriteRepository.findPropertyIdsByUserId(userId));
		bases.forEach(base -> excludedIds.add(base.getPropertyId()));
		return RecommendDto.SimilarListResponse.builder()
			.properties(toItems(pickSimilar(bases, userId, excludedIds, size)))
			.build();
	}

	// 최근 30일에 본 서로 다른 매물을 최근 순서로 3개까지
	private List<Property> readRecentViewed(Long userId) {
		Map<Long, Property> viewed = new LinkedHashMap<>();
		propertyViewLogRepository.findAllByUser_UserIdAndViewedAtAfter(userId, LocalDateTime.now().minusDays(SIGNAL_DAYS))
			.stream()
			.sorted(Comparator.comparing(PropertyViewLog::getViewedAt).reversed())
			.forEach(view -> viewed.putIfAbsent(view.getProperty().getPropertyId(), view.getProperty()));
		return viewed.values().stream().limit(SIMILAR_BASE_LIMIT).toList();
	}

	// 최근에 담은 관심 매물 3개까지. 담은 뒤 공개가 끝난 매물도 비교 기준으로는 쓴다
	private List<Property> readRecentFavorites(Long userId) {
		return favoriteRepository.findAllByUser_UserId(userId, PageRequest.of(0, SIMILAR_BASE_LIMIT,
				Sort.by(Sort.Direction.DESC, "createdAt", "favoritePropertyId")))
			.map(Favorite::getProperty)
			.getContent();
	}

	// 기준 매물마다 후보를 찾아 점수를 매기고, 후보마다 가장 높은 점수로 줄 세운다. 점수가 같으면 최근 공개 순서다
	private List<Property> pickSimilar(List<Property> bases, Long viewerId, Set<Long> excludedIds, int size) {
		Map<Long, Property> candidates = new HashMap<>();
		Map<Long, Integer> scores = new HashMap<>();
		for (Property base : bases) {
			for (Property candidate : propertyService.readSimilarCandidates(base, viewerId, CANDIDATE_LIMIT)) {
				if (!excludedIds.contains(candidate.getPropertyId())) {
					candidates.putIfAbsent(candidate.getPropertyId(), candidate);
					scores.merge(candidate.getPropertyId(), PropertySimilarity.score(base, candidate), Math::max);
				}
			}
		}
		return candidates.values().stream()
			.sorted(Comparator.comparingInt((Property property) -> scores.get(property.getPropertyId())).reversed()
				.thenComparing(Property::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(Property::getPropertyId, Comparator.reverseOrder()))
			.limit(clampSize(size))
			.toList();
	}

	// 매물 목록과 같은 카드 모양으로 바꾸고 대표 사진을 붙인다
	private List<PropertyDto.PropertyItemResponse> toItems(List<Property> properties) {
		Map<Long, String> thumbnailUrls = propertyService.readThumbnailUrls(properties);
		return properties.stream()
			.map(property -> PropertyDto.PropertyItemResponse.from(property, thumbnailUrls.get(property.getPropertyId())))
			.toList();
	}

	// 추천 개수는 1~12개. 범위를 벗어나면 가장 가까운 값으로 맞춘다
	private int clampSize(int size) {
		return Math.min(Math.max(size, 1), MAX_SIZE);
	}

	// 추천은 집을 찾는 사용자(USER)만 받는다. 중개사·관리자는 기록하지 않는다
	private User findRecommendTarget(Long userId) {
		return userRepository.findById(userId).filter(user -> user.getRole() == Role.USER).orElse(null);
	}

	// 매물의 시·구(도로명주소 앞 두 낱말)와 지하철역을 관심 지역으로 더한다
	private void addInterest(Map<String, Integer> regionScores, Map<String, Integer> stationScores, Property property,
			int weight) {
		String[] words = property.getRoadAddress().trim().split("\\s+");
		addScore(regionScores, words.length < 2 ? words[0] : words[0] + " " + words[1], weight);
		addScore(stationScores, property.getNearbySubwayStation(), weight);
	}

	private void addScore(Map<String, Integer> scores, String key, int weight) {
		if (key != null && !key.isBlank()) {
			scores.merge(key.trim(), weight, Integer::sum);
		}
	}

	private List<String> topInterests(Map<String, Integer> scores) {
		return scores.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.limit(MAX_INTERESTS)
			.map(Map.Entry::getKey)
			.toList();
	}

	// 공개 매물별 인기 점수. 최근 조회 수와 관심 수(× 3)를 더한다
	private Map<Long, Long> readPopularity(LocalDateTime since) {
		Map<Long, Long> popularity = new HashMap<>();
		for (PropertyCount views : propertyViewLogRepository.countViewsByProperty(since, PropertyStatus.ACTIVE)) {
			popularity.merge(views.getPropertyId(), views.getCount(), Long::sum);
		}
		for (PropertyCount favorites : favoriteRepository.countFavoritesByProperty(PropertyStatus.ACTIVE)) {
			popularity.merge(favorites.getPropertyId(), favorites.getCount() * FAVORITE_POPULARITY, Long::sum);
		}
		return popularity;
	}

	// 전체 인기 후보: 최근 공개 매물에 인기 점수가 있는 매물을 더한다. 기록이 아직 없으면 최근 공개 순서가 된다
	private List<Property> readGlobalCandidates(Long userId, Map<Long, Long> popularity) {
		Map<Long, Property> candidates = new LinkedHashMap<>();
		propertyService.readRecommendCandidates(List.of(), List.of(), userId, CANDIDATE_LIMIT)
			.forEach(property -> candidates.put(property.getPropertyId(), property));
		propertyRepository.findAllById(popularity.keySet()).stream()
			.filter(property -> !property.getLandlord().getUserId().equals(userId))
			.forEach(property -> candidates.putIfAbsent(property.getPropertyId(), property));
		return new ArrayList<>(candidates.values());
	}

	// 후보를 인기 순으로 세워 이미 고른 매물·담은 매물을 빼고 개수만큼 채운다. 점수가 같으면 최근 공개 순서를 지킨다
	private void pickPopular(Map<Long, Property> picked, List<Property> candidates, Map<Long, Long> popularity,
			Set<Long> favoritedIds, int limit) {
		candidates.stream()
			.sorted(Comparator.comparingLong((Property property) -> popularity.getOrDefault(property.getPropertyId(), 0L))
				.reversed())
			.filter(property -> !favoritedIds.contains(property.getPropertyId()))
			.filter(property -> !picked.containsKey(property.getPropertyId()))
			.limit(limit - picked.size())
			.forEach(property -> picked.put(property.getPropertyId(), property));
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
