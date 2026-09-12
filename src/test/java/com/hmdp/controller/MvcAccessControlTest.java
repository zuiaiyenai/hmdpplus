package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.ISeckillTopBuyerService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {VoucherController.class, UploadController.class, ShopController.class})
@ContextConfiguration(classes = {VoucherController.class, UploadController.class,
        ShopController.class, com.hmdp.config.MvcConfig.class})
class MvcAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IVoucherService voucherService;

    @MockBean
    private ISeckillTopBuyerService seckillTopBuyerService;

    @MockBean
    private IShopService shopService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void anonymousUsersCanQueryVoucherList() throws Exception {
        when(voucherService.queryVoucherOfShop(1L)).thenReturn(Result.ok());

        mockMvc.perform(get("/voucher/list/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void anonymousUsersCannotCreateVoucher() throws Exception {
        mockMvc.perform(post("/voucher")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUsersCannotUploadImages() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/blog")
                        .file("file", "image".getBytes()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousUsersCanQueryShopDetails() throws Exception {
        when(shopService.queryById(1L)).thenReturn(Result.ok());

        mockMvc.perform(get("/shop/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void anonymousUsersCannotCreateShops() throws Exception {
        mockMvc.perform(post("/shop")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
