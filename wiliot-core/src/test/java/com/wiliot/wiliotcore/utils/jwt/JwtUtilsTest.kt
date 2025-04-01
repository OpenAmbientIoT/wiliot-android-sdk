package com.wiliot.wiliotcore.utils.jwt

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class JwtUtilsTest {

    companion object {
        /**
         * {
         *   "alg": "RS256",
         *   "typ": "JWT",
         *   "kid": "1Im8qKXSq96kg3e76FwZ4KxQ8Oo"
         * }
         *
         * {
         *   "aud": "a1fad44a-76a2-4a3f-9307-3acafada1a9a",
         *   "exp": 1729594271,
         *   "iat": 1729593371,
         *   "iss": "wiliot.com",
         *   "sub": "10000000-2222-3333-4444-29ee46330125",
         *   "jti": "d0110411-113a-1168-1172-112b11a41161",
         *   "authenticationType": "REFRESH_TOKEN",
         *   "email": "test@test.com",
         *   "email_verified": true,
         *   "preferred_username": "test@test.com",
         *   "roles": [
         *     "admin",
         *     "professional-services",
         *     "gateway"
         *   ],
         *   "auth_time": 1729592461,
         *   "applicationId": "21111111-11e2-4c3f-1117-11c5fddb169d",
         *   "tid": "38623562-3435-3632-6538-653563353564",
         *   "lastName": "Test",
         *   "fullName": "Mobile Test",
         *   "owners": {
         *     "12345": {
         *       "roles": [
         *         "installer"
         *       ]
         *     },
         *     "23456": {
         *       "roles": [
         *         "admin"
         *       ]
         *     },
         *     "dev": {
         *       "roles": [
         *         "editor"
         *       ]
         *     },
         *     "auto": {
         *       "roles": [
         *         "editor"
         *       ]
         *     }
         *   },
         *   "firstName": "Test",
         *   "username": "test@test.com"
         * }
         */
        private const val testJwtToken = "ewogICJhbGciOiAiUlMyNTYiLAogICJ0eXAiOiAiSldUIiwKICAia2lkIjogIjFJbThxS1hTcTk2a2czZTc2RndaNEt4UThPbyIKfQ.eyJhdWQiOiJhMWZhZDQ0YS03NmEyLTRhM2YtOTMwNy0zYWNhZmFkYTFhOWEiLCJleHAiOjE3Mjk1OTQyNzEsImlhdCI6MTcyOTU5MzM3MSwiaXNzIjoid2lsaW90LmNvbSIsInN1YiI6IjEwMDAwMDAwLTIyMjItMzMzMy00NDQ0LTI5ZWU0NjMzMDEyNSIsImp0aSI6ImQwMTEwNDExLTExM2EtMTE2OC0xMTcyLTExMmIxMWE0MTE2MSIsImF1dGhlbnRpY2F0aW9uVHlwZSI6IlJFRlJFU0hfVE9LRU4iLCJlbWFpbCI6InRlc3RAdGVzdC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdEB0ZXN0LmNvbSIsInJvbGVzIjpbImFkbWluIiwicHJvZmVzc2lvbmFsLXNlcnZpY2VzIiwiZ2F0ZXdheSJdLCJhdXRoX3RpbWUiOjE3Mjk1OTI0NjEsImFwcGxpY2F0aW9uSWQiOiIyMTExMTExMS0xMWUyLTRjM2YtMTExNy0xMWM1ZmRkYjE2OWQiLCJ0aWQiOiIzODYyMzU2Mi0zNDM1LTM2MzItNjUzOC02NTM1NjMzNTM1NjQiLCJsYXN0TmFtZSI6IlRlc3QiLCJmdWxsTmFtZSI6Ik1vYmlsZSBUZXN0Iiwib3duZXJzIjp7IjEyMzQ1Ijp7InJvbGVzIjpbImluc3RhbGxlciJdfSwiZGV2Ijp7InJvbGVzIjpbImVkaXRvciJdfSwiYXV0byI6eyJyb2xlcyI6WyJlZGl0b3IiXX0sIjIzNDU2Ijp7InJvbGVzIjpbImFkbWluIl19fSwiZmlyc3ROYW1lIjoiVGVzdCIsInVzZXJuYW1lIjoidGVzdEB0ZXN0LmNvbSJ9.xVVoAwBDEBo6f_mLIJbSa97c123BaHJW9xf6iFm_1Vx6EZufQaBYn6jjUI4D22a5qb"
    }

    private lateinit var jwt: JWT

    @Before
    fun setUp() {
        jwt = JwtUtils.parseJwt(testJwtToken)
    }

    @Test
    fun `Common JWT decoding Test`() {
        assertEquals("aud correct:", "a1fad44a-76a2-4a3f-9307-3acafada1a9a", jwt.getAudience()[0])
        assertEquals("exp correct:", Date(1729594271 * 1000L), jwt.getExpiresAt())
        assertEquals("iat correct:", Date(1729593371 * 1000L), jwt.getIssuedAt())
        assertEquals("iss correct:", "wiliot.com", jwt.getIssuer())
        assertEquals("sub correct:", "10000000-2222-3333-4444-29ee46330125", jwt.getSubject())
        assertEquals("jti correct:", "d0110411-113a-1168-1172-112b11a41161", jwt.getId())
    }

    @Test
    fun `String claims test`() {
        assertEquals("string claim correct", "REFRESH_TOKEN", jwt.getClaim("authenticationType").asString())
    }

    @Test
    fun `Array claims test`() {
        assertTrue("array claim type correct", jwt.getClaim("roles").asArray(String::class.java) is Array)
        val rolesList = listOf(
            "admin",
            "professional-services",
            "gateway"
        )
        assertTrue(
            "array contains correct content",
            jwt.getClaim("roles").asArray(String::class.java)?.all { it in rolesList } == true
        )
    }

    @Test
    fun `Long claim correct`() {
        assertEquals("long claim correct", 1729592461L, jwt.getClaim("auth_time").asLong())
    }

}