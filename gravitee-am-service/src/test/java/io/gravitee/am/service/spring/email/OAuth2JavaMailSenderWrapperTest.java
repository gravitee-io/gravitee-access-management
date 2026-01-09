/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service.spring.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2JavaMailSenderWrapperTest {

    @Mock
    private JavaMailSenderImpl mockMailSender;

    @Mock
    private OAuth2TokenService mockTokenService;

    private OAuth2JavaMailSenderWrapper wrapper;

    private static final String TEST_ACCESS_TOKEN = "test-access-token-12345";

    @BeforeEach
    void setUp() {
        wrapper = new OAuth2JavaMailSenderWrapper(mockMailSender, mockTokenService);
        lenient().when(mockTokenService.getAccessToken()).thenReturn(TEST_ACCESS_TOKEN);
    }

    @Test
    void test_refreshTokenBeforeSend_singleMimeMessage() {
        // Given
        MimeMessage message = createMockMimeMessage();

        // When
        wrapper.send(message);

        // Then
        InOrder inOrder = inOrder(mockTokenService, mockMailSender);
        inOrder.verify(mockTokenService).getAccessToken();
        inOrder.verify(mockMailSender).setPassword(TEST_ACCESS_TOKEN);
        inOrder.verify(mockMailSender).send(message);
    }

    @Test
    void test_refreshTokenBeforeSend_multipleMimeMessages() {
        // Given
        MimeMessage message1 = createMockMimeMessage();
        MimeMessage message2 = createMockMimeMessage();

        // When
        wrapper.send(message1, message2);

        // Then
        InOrder inOrder = inOrder(mockTokenService, mockMailSender);
        inOrder.verify(mockTokenService).getAccessToken();
        inOrder.verify(mockMailSender).setPassword(TEST_ACCESS_TOKEN);
        inOrder.verify(mockMailSender).send(message1, message2);
    }

    @Test
    void test_refreshTokenBeforeSend_mimeMessagePreparator() {
        // Given
        MimeMessagePreparator preparator = mimeMessage -> {};

        // When
        wrapper.send(preparator);

        // Then
        InOrder inOrder = inOrder(mockTokenService, mockMailSender);
        inOrder.verify(mockTokenService).getAccessToken();
        inOrder.verify(mockMailSender).setPassword(TEST_ACCESS_TOKEN);
        inOrder.verify(mockMailSender).send(preparator);
    }

    @Test
    void test_refreshTokenBeforeSend_multipleMimeMessagePreparators() {
        // Given
        MimeMessagePreparator preparator1 = mimeMessage -> {};
        MimeMessagePreparator preparator2 = mimeMessage -> {};

        // When
        wrapper.send(preparator1, preparator2);

        // Then
        InOrder inOrder = inOrder(mockTokenService, mockMailSender);
        inOrder.verify(mockTokenService).getAccessToken();
        inOrder.verify(mockMailSender).setPassword(TEST_ACCESS_TOKEN);
        inOrder.verify(mockMailSender).send(preparator1, preparator2);
    }

    @Test
    void test_refreshTokenBeforeSend_simpleMailMessage() {
        // Given
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("test@example.com");
        message.setSubject("Test");

        // When
        wrapper.send(message);

        // Then
        InOrder inOrder = inOrder(mockTokenService, mockMailSender);
        inOrder.verify(mockTokenService).getAccessToken();
        inOrder.verify(mockMailSender).setPassword(TEST_ACCESS_TOKEN);
        inOrder.verify(mockMailSender).send(message);
    }

    @Test
    void test_refreshTokenBeforeSend_multipleSimpleMailMessages() {
        // Given
        SimpleMailMessage message1 = new SimpleMailMessage();
        SimpleMailMessage message2 = new SimpleMailMessage();

        // When
        wrapper.send(message1, message2);

        // Then
        InOrder inOrder = inOrder(mockTokenService, mockMailSender);
        inOrder.verify(mockTokenService).getAccessToken();
        inOrder.verify(mockMailSender).setPassword(TEST_ACCESS_TOKEN);
        inOrder.verify(mockMailSender).send(message1, message2);
    }

    @Test
    void test_createMimeMessage_noTokenRefresh() {
        // Given
        MimeMessage mockMessage = createMockMimeMessage();
        when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);

        // When
        MimeMessage result = wrapper.createMimeMessage();

        // Then
        assertNotNull(result);
        verify(mockMailSender).createMimeMessage();
        verify(mockTokenService, never()).getAccessToken();
        verify(mockMailSender, never()).setPassword(any());
    }

    @Test
    void test_createMimeMessageWithInputStream_noTokenRefresh() {
        // Given
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());
        MimeMessage mockMessage = createMockMimeMessage();
        when(mockMailSender.createMimeMessage(inputStream)).thenReturn(mockMessage);

        // When
        MimeMessage result = wrapper.createMimeMessage(inputStream);

        // Then
        assertNotNull(result);
        verify(mockMailSender).createMimeMessage(inputStream);
        verify(mockTokenService, never()).getAccessToken();
        verify(mockMailSender, never()).setPassword(any());
    }

    @Test
    void test_tokenRefreshFailure_propagatesException() {
        // Given
        MimeMessage message = createMockMimeMessage();
        RuntimeException tokenException = new RuntimeException("Token refresh failed");
        when(mockTokenService.getAccessToken()).thenThrow(tokenException);

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            wrapper.send(message);
        });

        assertEquals("Token refresh failed", exception.getMessage());
        verify(mockTokenService).getAccessToken();
        verify(mockMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void test_sendFailure_propagatesException() {
        // Given
        MimeMessage message = createMockMimeMessage();
        RuntimeException sendException = new RuntimeException("Send failed");
        doThrow(sendException).when(mockMailSender).send(message);

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            wrapper.send(message);
        });

        assertEquals("Send failed", exception.getMessage());
        verify(mockTokenService).getAccessToken();
        verify(mockMailSender).setPassword(TEST_ACCESS_TOKEN);
        verify(mockMailSender).send(message);
    }

    @Test
    void test_threadSafety_concurrentSends() throws InterruptedException {
        // Given
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        when(mockTokenService.getAccessToken()).thenAnswer(invocation -> {
            Thread.sleep(10);
            return TEST_ACCESS_TOKEN;
        });

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    MimeMessage message = createMockMimeMessage();
                    wrapper.send(message);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

        // Then
        assertTrue(completed, "All threads should complete within timeout");
        assertEquals(threadCount, successCount.get(), "All sends should succeed");
        assertEquals(0, failureCount.get(), "No sends should fail");

        verify(mockTokenService, times(threadCount)).getAccessToken();
        verify(mockMailSender, times(threadCount)).setPassword(TEST_ACCESS_TOKEN);
        verify(mockMailSender, times(threadCount)).send(any(MimeMessage.class));

        executorService.shutdown();
    }

    @Test
    void test_multipleSequentialSends_refreshesEachTime() {
        // Given
        MimeMessage message1 = createMockMimeMessage();
        MimeMessage message2 = createMockMimeMessage();
        MimeMessage message3 = createMockMimeMessage();

        // When
        wrapper.send(message1);
        wrapper.send(message2);
        wrapper.send(message3);

        // Then
        verify(mockTokenService, times(3)).getAccessToken();
        verify(mockMailSender, times(3)).setPassword(TEST_ACCESS_TOKEN);
        verify(mockMailSender).send(message1);
        verify(mockMailSender).send(message2);
        verify(mockMailSender).send(message3);
    }

    @Test
    void test_tokenChanges_usesNewToken() {
        // Given
        MimeMessage message1 = createMockMimeMessage();
        MimeMessage message2 = createMockMimeMessage();

        String firstToken = "first-token";
        String secondToken = "second-token";

        when(mockTokenService.getAccessToken())
                .thenReturn(firstToken)
                .thenReturn(secondToken);

        // When
        wrapper.send(message1);
        wrapper.send(message2);

        // Then
        InOrder inOrder = inOrder(mockTokenService, mockMailSender);

        inOrder.verify(mockTokenService).getAccessToken();
        inOrder.verify(mockMailSender).setPassword(firstToken);
        inOrder.verify(mockMailSender).send(message1);

        inOrder.verify(mockTokenService).getAccessToken();
        inOrder.verify(mockMailSender).setPassword(secondToken);
        inOrder.verify(mockMailSender).send(message2);
    }

    private MimeMessage createMockMimeMessage() {
        Session session = Session.getInstance(new Properties());
        return new MimeMessage(session);
    }
}
