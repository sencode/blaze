(ns blaze.openid-auth-test
  (:require
    [blaze.openid-auth :refer [public-key]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]])
  (:import
    [java.security PublicKey]))


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


;; The following json has been taken from https://samples.auth0.com/.well-known/jwks.json
(def jwks-json "{\"keys\":[{\"alg\":\"RS256\",\"kty\":\"RSA\",\"use\":\"sig\",\"x5c\":[\"MIIDCzCCAfOgAwIBAgIJAJP6qydiMpsuMA0GCSqGSIb3DQEBBQUAMBwxGjAYBgNVBAMMEXNhbXBsZXMuYXV0aDAuY29tMB4XDTE0MDUyNjIyMDA1MFoXDTI4MDIwMjIyMDA1MFowHDEaMBgGA1UEAwwRc2FtcGxlcy5hdXRoMC5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCkH4CFGSJ4s3mwCBzaGGwxa9Jxzfb1ia4nUumxbsuaB7PClZZgrNQiOR3MXVNV9W6F1D+wjT6oFHOo7TOkVI22I/ff3XZTE0F35UUHGWRtiQ4LdZxwOPTed2Lax3F2DEyl3Y0CguUKbq2sSghvHYcggM6aj3N53VBsnBh/kdrURDLx1RYqBIL6Fvkhb/V/v/u9UKhZM0CDQRef9FZ7R8q9ie9cnbDOj1dT9d64kiJIYtTraG0gOrs4LI+4KK0EZu5R7Uo053IK7kfNasWhDkl8yxNYkDxwfcIuAcDmLgLnAI4tfW5beJuw+/w75PO/EwzwsnvppXaAz7e3Wf8g1yWFAgMBAAGjUDBOMB0GA1UdDgQWBBTsmytFLNox+NUZdTNlCUL3hHrngTAfBgNVHSMEGDAWgBTsmytFLNox+NUZdTNlCUL3hHrngTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBQUAA4IBAQAodbRX/34LnWB70l8dpDF1neDoG29F0XdpE9ICWHeWB1gb/FvJ5UMy9/pnL0DI3mPwkTDDob+16Zc68o6dT6sH3vEUP1iRreJlFADEmJZjrH9P4Y7ttx3G2Uw2RU5uucXIqiyMDBrQo4vx4Lnghl+b/WYbZJgzLfZLgkOEjcznS0Yi5Wdz6MvaL3FehSfweHyrjmxz0e8elHq7VY8OqRA+4PmUBce9BgDCk9fZFjgj8l0m9Vc5pPKSY9LMmTyrYkeDr/KppqdXKOCHmv7AIGb6rMCtbkIL/CM7Bh9Hx78/UKAz87Sl9A1yXVNjKbZwOEW60ORIwJmd8Tv46gJF+/rV\"],\"n\":\"pB-AhRkieLN5sAgc2hhsMWvScc329YmuJ1LpsW7LmgezwpWWYKzUIjkdzF1TVfVuhdQ_sI0-qBRzqO0zpFSNtiP33912UxNBd-VFBxlkbYkOC3WccDj03ndi2sdxdgxMpd2NAoLlCm6trEoIbx2HIIDOmo9zed1QbJwYf5Ha1EQy8dUWKgSC-hb5IW_1f7_7vVCoWTNAg0EXn_RWe0fKvYnvXJ2wzo9XU_XeuJIiSGLU62htIDq7OCyPuCitBGbuUe1KNOdyCu5HzWrFoQ5JfMsTWJA8cH3CLgHA5i4C5wCOLX1uW3ibsPv8O-TzvxMM8LJ76aV2gM-3t1n_INclhQ\",\"e\":\"AQAB\",\"kid\":\"NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg\",\"x5t\":\"NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg\"}]}")


(deftest public-key-test
  (testing "Returns type PublicKey after parsing jwks-json string"
    (let [k (public-key jwks-json)]
      (is (instance? PublicKey k)))))
