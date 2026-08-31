package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.hmdp.mapper.BlogCommentsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogCommentsServiceImplTest {

    @Mock
    private BlogCommentsMapper blogCommentsMapper;

    private BlogCommentsServiceImpl blogCommentsService;

    @BeforeEach
    void setUp() {
        blogCommentsService = new BlogCommentsServiceImpl();
        ReflectionTestUtils.setField(blogCommentsService, "baseMapper", blogCommentsMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnCommentsWrittenByUser() {
        when(blogCommentsMapper.selectList(any())).thenReturn(Arrays.asList(
                new BlogComments().setId(2L).setUserId(9L),
                new BlogComments().setId(1L).setUserId(9L)));

        Result result = blogCommentsService.queryByUserId(9L);

        assertTrue(result.getSuccess());
        List<BlogComments> comments = (List<BlogComments>) result.getData();
        assertEquals(Arrays.asList(2L, 1L), Arrays.asList(comments.get(0).getId(), comments.get(1).getId()));
    }
}
