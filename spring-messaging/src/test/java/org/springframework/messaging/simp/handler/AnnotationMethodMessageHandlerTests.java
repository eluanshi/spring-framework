/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.messaging.simp.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.PathVariable;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeEvent;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Controller;

import static org.junit.Assert.*;


/**
 * Test fixture for {@link AnnotationMethodMessageHandler}.
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 */
public class AnnotationMethodMessageHandlerTests {

	private TestAnnotationMethodMessageHandler messageHandler;

	private TestController testController;


	@Before
	public void setup() {
		MessageChannel channel = Mockito.mock(MessageChannel.class);
		SimpMessageSendingOperations brokerTemplate = new SimpMessagingTemplate(channel);
		this.messageHandler = new TestAnnotationMethodMessageHandler(brokerTemplate, channel);
		this.messageHandler.setApplicationContext(new StaticApplicationContext());
		this.messageHandler.afterPropertiesSet();

		testController = new TestController();
		this.messageHandler.registerHandler(testController);
	}


	@SuppressWarnings("unchecked")
	@Test
	public void headerArgumentResolution() {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
		headers.setDestination("/headers");
		headers.setHeader("foo", "bar");
		Message<?> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();
		this.messageHandler.handleMessage(message);

		assertEquals("headers", this.testController.method);
		assertEquals("bar", this.testController.arguments.get("foo"));
		assertEquals("bar", ((Map<String, Object>) this.testController.arguments.get("headers")).get("foo"));
	}

	@Test(expected=IllegalStateException.class)
	public void duplicateMappings() {
		this.messageHandler.registerHandler(new DuplicateMappingController());
	}

	@Test
	public void messageMappingPathVariableResolution() {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
		headers.setDestination("/message/bar/value");
		Message<?> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();
		this.messageHandler.handleMessage(message);

		assertEquals("messageMappingPathVariable", this.testController.method);
		assertEquals("bar", this.testController.arguments.get("foo"));
		assertEquals("value", this.testController.arguments.get("name"));
	}

	@Test
	public void subscribeEventPathVariableResolution() {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
		headers.setDestination("/sub/bar/value");
		Message<?> message = MessageBuilder.withPayload(new byte[0])
				.copyHeaders(headers.toMap()).build();
		this.messageHandler.handleMessage(message);

		assertEquals("subscribeEventPathVariable", this.testController.method);
		assertEquals("bar", this.testController.arguments.get("foo"));
		assertEquals("value", this.testController.arguments.get("name"));
	}

	@Test
	public void antPatchMatchWildcard() {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
		headers.setDestination("/pathmatch/wildcard/test");
		Message<?> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();
		this.messageHandler.handleMessage(message);

		assertEquals("pathMatchWildcard", this.testController.method);
	}

	@Test
	public void bestMatchWildcard() {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
		headers.setDestination("/bestmatch/bar/path");
		Message<?> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();
		this.messageHandler.handleMessage(message);

		assertEquals("bestMatch", this.testController.method);
		assertEquals("bar", this.testController.arguments.get("foo"));
	}

	@Test
	public void simpleBinding() {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
		headers.setDestination("/binding/id/12");
		Message<?> message = MessageBuilder.withPayload(new byte[0]).setHeaders(headers).build();
		this.messageHandler.handleMessage(message);

		assertEquals("simpleBinding", this.testController.method);
		assertTrue("should be bound to type long", this.testController.arguments.get("id") instanceof Long);
		assertEquals(12L, this.testController.arguments.get("id"));
	}

	private static class TestAnnotationMethodMessageHandler extends AnnotationMethodMessageHandler {

		public TestAnnotationMethodMessageHandler(SimpMessageSendingOperations brokerTemplate,
				MessageChannel webSocketResponseChannel) {

			super(brokerTemplate, webSocketResponseChannel);
		}

		public void registerHandler(Object handler) {
			super.detectHandlerMethods(handler);
		}
	}


	@Controller
	private static class TestController {

		private String method;

		private Map<String, Object> arguments = new LinkedHashMap<String, Object>();


		@MessageMapping("/headers")
		public void headers(@Header String foo, @Headers Map<String, Object> headers) {
			this.method = "headers";
			this.arguments.put("foo", foo);
			this.arguments.put("headers", headers);
		}

		@MessageMapping("/message/{foo}/{name}")
		public void messageMappingPathVariable(@PathVariable("foo") String param1,
		                                       @PathVariable("name") String param2) {
			this.method = "messageMappingPathVariable";
			this.arguments.put("foo", param1);
			this.arguments.put("name", param2);
		}

		@SubscribeEvent("/sub/{foo}/{name}")
		public void subscribeEventPathVariable(@PathVariable("foo") String param1,
		                                       @PathVariable("name") String param2) {
			this.method = "subscribeEventPathVariable";
			this.arguments.put("foo", param1);
			this.arguments.put("name", param2);
		}

		@MessageMapping("/pathmatch/wildcard/**")
		public void pathMatchWildcard() {
			this.method = "pathMatchWildcard";
		}

		@MessageMapping("/bestmatch/{foo}/path")
		public void bestMatch(@PathVariable("foo") String param1) {
			this.method = "bestMatch";
			this.arguments.put("foo", param1);
		}

		@MessageMapping("/bestmatch/**")
		public void otherMatch() {
			this.method = "otherMatch";
		}

		@MessageMapping("/binding/id/{id}")
		public void simpleBinding(@PathVariable("id") Long id) {
			this.method = "simpleBinding";
			this.arguments.put("id", id);
		}
	}

	@Controller
	private static class DuplicateMappingController {

		@MessageMapping(value="/duplicate")
		public void handle1() { }

		@MessageMapping(value="/duplicate")
		public void handle2() { }
	}

}
