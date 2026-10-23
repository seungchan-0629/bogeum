package com.bogeum.dto;

import java.util.List;
import com.bogeum.security.EmailNormalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocialJoinDTO {
	@NotBlank
	@Email
	@Size(max = 254)
	private String email;

	@NotBlank
	@Size(max = 200)
	private String identityVerificationToken;

	@Valid
	@NotEmpty
	@Size(max = 2)
	private List<@NotNull ConsentAgreementDTO> consents;

	public void setEmail(String email) {
		this.email = EmailNormalizer.normalize(email);
	}
}
