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
package io.gravitee.am.service.utils;

import io.gravitee.am.common.email.Email;
import io.gravitee.am.service.exception.BatchEmailException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private EmailSender emailSender;

    private static final String TEMPLATES_PATH = "/templates";

    @Before
    public void setUp() {
        emailSender = new EmailSender(mailSender, TEMPLATES_PATH);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    public void shouldReturnEarlyWhenEmailListIsEmpty() {
        // Given
        List<Email> emptyList = Collections.emptyList();

        // When
        emailSender.batch(emptyList);

        // Then
        verify(mailSender, never()).send(any(MimeMessage[].class));
    }

    @Test
    public void shouldSendBatchEmailsSuccessfully() {
        // Given
        List<Email> emails = Arrays.asList(
                createEmail("user1@gravitee.io"),
                createEmail("user2@gravitee.io"),
                createEmail("user3@gravitee.io")
        );

        // When
        emailSender.batch(emails);

        // Then
        verify(mailSender, times(1)).send(any(MimeMessage[].class));
    }

    @Test
    public void shouldThrowBatchEmailExceptionOnMailSendExceptionWithoutFailedMessages() {
        // Given
        List<Email> emails = Arrays.asList(
                createEmail("user1@gravitee.io"),
                createEmail("user2@gravitee.io")
        );

        MailSendException mailSendException = new MailSendException("SMTP server error");

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(mailSendException).when(mailSender).send(any(MimeMessage[].class));

        // When & Then
        assertThatThrownBy(() -> emailSender.batch(emails))
                .isInstanceOf(BatchEmailException.class)
                .hasMessageContaining("Error while creating emails")
                .satisfies(ex -> {
                    BatchEmailException batchEx = (BatchEmailException) ex;
                    assertThat(batchEx.getEmails()).containsExactly("user1@gravitee.io", "user2@gravitee.io");
                });
    }

    @Test
    public void shouldThrowBatchEmailExceptionOnMailSendExceptionWithFailedMessages() throws MessagingException {
        // Given
        List<Email> emails = Arrays.asList(
                createEmail("user1@gravitee.io"),
                createEmail("user2@gravitee.io"),
                createEmail("user3@gravitee.io")
        );

        // Create mock failed messages
        MimeMessage failedMsg1 = org.mockito.Mockito.mock(MimeMessage.class);
        MimeMessage failedMsg2 = org.mockito.Mockito.mock(MimeMessage.class);

        InternetAddress address1 = new InternetAddress("user1@gravitee.io");
        InternetAddress address2 = new InternetAddress("user3@gravitee.io");

        when(failedMsg1.getRecipients(Message.RecipientType.TO))
                .thenReturn(new InternetAddress[]{address1});
        when(failedMsg2.getRecipients(Message.RecipientType.TO))
                .thenReturn(new InternetAddress[]{address2});

        Map<Object, Exception> failedMessages = new HashMap<>();
        failedMessages.put(failedMsg1, new MessagingException("Failed to send to user1"));
        failedMessages.put(failedMsg2, new MessagingException("Failed to send to user3"));

        MailSendException mailSendException = new MailSendException(failedMessages);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(mailSendException).when(mailSender).send(any(MimeMessage[].class));

        // When & Then
        assertThatThrownBy(() -> emailSender.batch(emails))
                .isInstanceOf(BatchEmailException.class)
                .hasMessageContaining("Error while creating emails")
                .satisfies(ex -> {
                    BatchEmailException batchEx = (BatchEmailException) ex;
                    // Should return all email addresses from the original list
                    assertThat(batchEx.getEmails()).containsExactlyInAnyOrder(
                            "user1@gravitee.io",
                            "user3@gravitee.io"
                    );
                });
    }

    @Test
    public void shouldThrowBatchEmailExceptionOnGenericException() {
        // Given
        List<Email> emails = Arrays.asList(
                createEmail("user1@gravitee.io"),
                createEmail("user2@gravitee.io")
        );

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("Unexpected error")).when(mailSender).send(any(MimeMessage[].class));

        // When & Then
        assertThatThrownBy(() -> emailSender.batch(emails))
                .isInstanceOf(BatchEmailException.class)
                .hasMessageContaining("Error while creating email")
                .satisfies(ex -> {
                    BatchEmailException batchEx = (BatchEmailException) ex;
                    assertThat(batchEx.getEmails()).containsExactly("user1@gravitee.io", "user2@gravitee.io");
                });
    }

    @Test
    public void shouldHandleMessagingExceptionWhenExtractingFailedAddresses() throws MessagingException {
        // Given
        List<Email> emails = Arrays.asList(
                createEmail("user1@gravitee.io"),
                createEmail("user2@gravitee.io")
        );

        // Create mock failed messages where getRecipients throws MessagingException
        MimeMessage failedMsg1 = org.mockito.Mockito.mock(MimeMessage.class);
        MimeMessage failedMsg2 = org.mockito.Mockito.mock(MimeMessage.class);

        InternetAddress address1 = new InternetAddress("user1@gravitee.io");
        when(failedMsg1.getRecipients(Message.RecipientType.TO))
                .thenReturn(new InternetAddress[]{address1});
        when(failedMsg2.getRecipients(Message.RecipientType.TO))
                .thenThrow(new MessagingException("Cannot extract recipients"));

        Map<Object, Exception> failedMessages = new HashMap<>();
        failedMessages.put(failedMsg1, new MessagingException("Failed to send"));
        failedMessages.put(failedMsg2, new MessagingException("Failed to send"));

        MailSendException mailSendException = new MailSendException(failedMessages);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(mailSendException).when(mailSender).send(any(MimeMessage[].class));

        // When & Then
        assertThatThrownBy(() -> emailSender.batch(emails))
                .isInstanceOf(BatchEmailException.class)
                .hasMessageContaining("Error while creating emails")
                .satisfies(ex -> {
                    BatchEmailException batchEx = (BatchEmailException) ex;
                    // Should still return all original email addresses
                    assertThat(batchEx.getEmails()).containsExactly("user1@gravitee.io");
                });
    }

    @Test
    public void shouldHandleSingleEmailInBatch() {
        // Given
        List<Email> emails = Collections.singletonList(createEmail("single@gravitee.io"));

        // When
        emailSender.batch(emails);

        // Then
        verify(mailSender, times(1)).send(any(MimeMessage[].class));
    }

    @Test
    public void shouldHandleEmailsWithMultipleRecipients() {
        // Given
        Email email = createEmail("user1@gravitee.io", "user2@gravitee.io", "user3@gravitee.io");
        List<Email> emails = Collections.singletonList(email);

        // When
        emailSender.batch(emails);

        // Then
        verify(mailSender, times(1)).send(any(MimeMessage[].class));
    }

    @Test
    public void shouldExtractOnlyFirstRecipientFromEachEmail() {
        // Given
        List<Email> emails = Arrays.asList(
                createEmail("user1@gravitee.io", "cc1@gravitee.io"),
                createEmail("user2@gravitee.io", "cc2@gravitee.io")
        );

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("Test exception")).when(mailSender).send(any(MimeMessage[].class));

        // When & Then
        assertThatThrownBy(() -> emailSender.batch(emails))
                .isInstanceOf(BatchEmailException.class)
                .satisfies(ex -> {
                    BatchEmailException batchEx = (BatchEmailException) ex;
                    // Should extract only the first recipient from each email's 'to' array
                    assertThat(batchEx.getEmails()).containsExactly("user1@gravitee.io", "user2@gravitee.io");
                });
    }

    private Email createEmail(String... recipients) {
        Email email = new Email();
        email.setFrom("from@gravitee.io");
        email.setFromName("Gravitee");
        email.setTo(recipients);
        email.setSubject("Test Subject");
        email.setContent("<html><body>Test Content</body></html>");
        return email;
    }
}
