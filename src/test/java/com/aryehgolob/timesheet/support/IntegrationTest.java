package com.aryehgolob.timesheet.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.aryehgolob.timesheet.TestcontainersConfiguration;

/**
 * Base for tests that need the whole app on real Postgres. Every subclass has
 * the same configuration, so Spring reuses one context and one container.
 * Tests isolate themselves by creating their own users rather than by wiping tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestData.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestData data;
}
