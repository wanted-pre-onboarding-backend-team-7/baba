package org.example.baba.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.example.baba.controller.dto.response.PostDetailResponseDto;
import org.example.baba.controller.dto.response.PostSimpleResponseDto;
import org.example.baba.domain.HashTag;
import org.example.baba.domain.Post;
import org.example.baba.domain.PostHashTagMap;
import org.example.baba.domain.enums.SNSType;
import org.example.baba.exception.CustomException;
import org.example.baba.exception.exceptionType.PostExceptionType;
import org.example.baba.repository.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class PostServiceTest {

  @Mock private PostRepository postRepository;
  @InjectMocks private PostService postService;

  @Test
  @DisplayName("게시글 상세정보 확인")
  void getPostDetail() {

    // given
    Long postId = 1L;

    Post post = new Post(postId, SNSType.INSTAGRAM, "Title", "Content", 0, 0, 0);
    HashTag hashTag = new HashTag(1L, "코딩");
    PostHashTagMap postHashTagMap = new PostHashTagMap(1L, post, hashTag);
    post.getPostHashTags().add(postHashTagMap);

    when(postRepository.findById(postId)).thenReturn(Optional.of(post));

    log.info("서비스 호출 전 viewCount: {}", post.getViewCount()); // = 0

    // when
    PostDetailResponseDto detail = postService.getPostDetail(postId);

    log.info("서비스 호출 후 viewCount: {} ", post.getViewCount()); // = 1

    // then
    assertNotNull(detail);
    assertEquals(postId, detail.getId());
    assertEquals("코딩", detail.getHashtags().get(0));
    assertEquals(1, post.getViewCount()); // viewCount 증가 수 재검증 0->1
    assertEquals(0, post.getLikeCount());
    assertEquals(0, post.getShareCount());
    verify(postRepository, times(1)).findById(postId);
    // verify(post, times(2)).view(); 메서드 2회 호출 감지 시 (X)
  }

  @Test
  @DisplayName("게시글이 존재하지 않을 때 예외 처리")
  void getPostNotFound() {

    // given
    Long postId = 1L;
    when(postRepository.findById(postId)).thenReturn(Optional.empty());

    // when & then
    CustomException thrown =
        assertThrows(
            CustomException.class,
            () -> {
              postService.getPostDetail(postId);
            });

    // 설정해둔 예외 값과 일치하는 지 확인
    assertEquals(PostExceptionType.NOT_FOUND_POST, thrown.getExceptionType());
  }

  @Test
  @DisplayName("게시글 좋아요를 클릭하여 좋아요 수가 1 증가합니다.")
  void like_post_increases_count_by_one() {
    // given
    Long postId = 1L;
    SNSType type = SNSType.FACEBOOK;
    int initialLikeCount = 10;

    // 실제 객체 생성
    Post post = Post.builder().id(postId).type(type).likeCount(initialLikeCount).build();

    // Mock 설정
    when(postRepository.findByIdAndType(postId, type)).thenReturn(Optional.of(post));

    // when
    postService.likePost(postId, type);

    // then
    verify(postRepository, times(1)).findByIdAndType(postId, type);
    assertEquals(initialLikeCount + 1, post.getLikeCount(), "좋아요 수가 11이 되어야 합니다.");
  }

  @Test
  @DisplayName("게시글이 존재하지 않아 좋아요에 실패합니다.")
  void like_post_failed_when_post_not_found() {
    // given
    Long postId = 1L;
    SNSType type = SNSType.FACEBOOK;

    when(postRepository.findByIdAndType(postId, type)).thenReturn(Optional.empty());

    // when
    CustomException exception =
        assertThrows(CustomException.class, () -> postService.likePost(postId, type));

    // then
    assertEquals(
        PostExceptionType.NOT_FOUND_POST,
        exception.getExceptionType(),
        "좋아요 할 게시글이 존재하지 않아 NOT_FOUND_POST 예외가 발생합니다.");
  }

  @Test
  @DisplayName("모든 쿼리 파라미터로 게시글 조회하여 성공합니다.")
  void getPosts_with_all_query_parameters() {
    // given
    // 쿼리 파라미터 준비
    String hashtag = "개발";
    SNSType type = SNSType.FACEBOOK;
    String searchBy = "title, content";
    String searchKeyword = "원티드";
    String orderBy = "viewCount";
    String orderDirection = "ASC";
    int page = 0;
    int size = 2;

    List<Post> posts =
        Arrays.asList(
            Post.builder()
                .id(1L)
                .type(type)
                .title("원티드 8월 챌린지 하는 사람?")
                .content("포트폴리오 주제래")
                .viewCount(10)
                .build(),
            Post.builder()
                .id(2L)
                .type(SNSType.INSTAGRAM)
                .title("개발 중")
                .content("빡코딩")
                .viewCount(2)
                .build(),
            Post.builder()
                .id(3L)
                .type(type)
                .title("원티드 인턴쉽 하는 중")
                .content("존잼")
                .viewCount(31)
                .build(),
            Post.builder()
                .id(4L)
                .type(type)
                .title("원티드랩")
                .content("원티드랩 공고 떴다.")
                .viewCount(22)
                .build(),
            Post.builder()
                .id(5L)
                .type(type)
                .title("노래 뭐들어?")
                .content("플밍하면서 들을만한 노래 좀 ㅊㅊ")
                .viewCount(8)
                .build());
    List<Post> expectedPosts = Arrays.asList(posts.get(0), posts.get(3), posts.get(2));
    Page<Post> postPage =
        new PageImpl<>(
            expectedPosts,
            PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(orderDirection), orderBy)),
            expectedPosts.size());

    when(postRepository.findPosts(
            hashtag,
            type,
            searchBy,
            searchKeyword,
            PageRequest.of(
                page, size, Sort.by(Sort.Direction.fromString(orderDirection), orderBy))))
        .thenReturn(postPage);

    // when
    Page<PostSimpleResponseDto> result =
        postService.getPosts(
            hashtag, type, searchBy, searchKeyword, orderBy, orderDirection, page, size);

    // then
    verify(postRepository, times(1))
        .findPosts(
            hashtag,
            type,
            searchBy,
            searchKeyword,
            PageRequest.of(
                page, size, Sort.by(Sort.Direction.fromString(orderDirection), orderBy)));

    assertNotNull(result, "결과 페이지는 null 이 아니어야 합니다.");
    assertEquals(expectedPosts.size(), result.getTotalElements(), "검색하여 나온 게시글 총 개수를 검증합니다.");
    assertEquals(2, result.getTotalPages(), "검색하여 나온 게시글 총 페이지수를 검증합니다.");

    assertEquals(
        expectedPosts.get(0).getId(), result.getContent().get(0).getId(), "첫 번째 게시글 ID를 검증합니다.");
    assertEquals(
        expectedPosts.get(1).getId(), result.getContent().get(1).getId(), "두 번째 게시글 ID를 검증합니다.");
    assertEquals(
        expectedPosts.get(2).getId(), result.getContent().get(2).getId(), "세 번째 게시글 ID를 검증합니다.");

    assertEquals(
        expectedPosts.get(0).getViewCount(),
        result.getContent().get(0).getViewCount(),
        "첫 번째 게시글의 조회 수를 검증합니다.");
    assertEquals(
        expectedPosts.get(1).getViewCount(),
        result.getContent().get(1).getViewCount(),
        "두 번째 게시글의 조회 수를 검증합니다.");
    assertEquals(
        expectedPosts.get(2).getViewCount(),
        result.getContent().get(2).getViewCount(),
        "세 번째 게시글의 조회 수를 검증합니다.");

    assertEquals(type, result.getContent().get(0).getType(), "첫 번째 게시글의 SNS 타입을 검증합니다.");
    assertEquals(type, result.getContent().get(1).getType(), "두 번째 게시글의 SNS 타입을 검증합니다.");
    assertEquals(type, result.getContent().get(2).getType(), "세 번째 게시글의 SNS 타입을 검증합니다.");
  }

  @Test
  @DisplayName("게시글 공유를 클릭하여 공유 수가 1 증가합니다.")
  void share_post_increases_count_by_one() {
    // given
    Long postId = 1L;
    SNSType type = SNSType.FACEBOOK;
    int initialShareCount = 10;

    // 실제 객체 생성
    Post post = Post.builder().id(postId).type(type).shareCount(initialShareCount).build();

    // Mock 설정
    when(postRepository.findByIdAndType(postId, type)).thenReturn(Optional.of(post));

    // when
    postService.sharePost(postId, type);

    // then
    verify(postRepository, times(1)).findByIdAndType(postId, type);
    assertEquals(initialShareCount + 1, post.getShareCount(), "공유 수가 11이 되어야 합니다.");
  }

  @Test
  @DisplayName("게시글이 존재하지 않아 공유에 실패합니다.")
  void share_post_failed_when_post_not_found() {
    // given
    Long postId = 1L;
    SNSType type = SNSType.FACEBOOK;

    when(postRepository.findByIdAndType(postId, type)).thenReturn(Optional.empty());

    // when
    CustomException exception =
        assertThrows(CustomException.class, () -> postService.sharePost(postId, type));

    // then
    assertEquals(
        PostExceptionType.NOT_FOUND_POST,
        exception.getExceptionType(),
        "공유 할 게시글이 존재하지 않아 NOT_FOUND_POST 예외가 발생합니다.");
  }
}
