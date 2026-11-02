package com.soksak.soksak.user.dto;

import com.soksak.soksak.common.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Year;
import java.util.Locale;

public record CreateUserRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String email,

        @NotBlank(message = "아이디는 필수입니다.")
        @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
        @Pattern(regexp = "^[a-z0-9]+$", message = "아이디는 영문 소문자와 숫자만 사용할 수 있습니다.")
        String loginId,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
        String nickname,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$",
                message = "비밀번호는 영문·숫자·특수문자만 쓸 수 있고, 영문과 숫자를 각각 하나 이상 포함해야 합니다."
        )
        String password,

        // 올해 이후는 여기서 막고, 너무 이른 연도는 UserService에서 막는다.
        @NotNull(message = "출생연도는 필수입니다.")
        @PastOrPresent(message = "출생연도는 올해 이후일 수 없습니다.")
        Year birthYear,

        @NotNull(message = "성별은 필수입니다.")
        Gender gender
) {
    // 닉네임은 앞뒤 공백을 떼고 저장한다. " foo"와 "foo"를 같은 닉네임으로 보기 위해서다.
    // 이메일은 소문자로 바꿔 저장해서 "A@x.com"과 "a@x.com"을 같은 이메일로 본다.
    // 서버 로케일에 따라 대소문자 변환이 달라지지 않게 Locale.ROOT를 쓴다.
    // @NotBlank와 @Size 검증은 생성 뒤에 돌아서 바꾼 값으로 검사한다.
    public CreateUserRequest {
        if (nickname != null) nickname = nickname.strip();
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }
}