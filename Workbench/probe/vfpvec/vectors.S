	.arm
	.text
	.global vadd8
@ vadd8(a, b, c): c[0..7] = a + b, LEN 8
vadd8:
	vmrs r3, fpscr
	bic r3, r3, #0x370000
	orr r3, r3, #0x70000
	vmsr fpscr, r3
	vldmia r0, {s8-s15}
	vldmia r1, {s16-s23}
	vadd.f32 s24, s8, s16
	vstmia r2, {s24-s31}
	bic r3, r3, #0x370000
	vmsr fpscr, r3
	bx lr
	.global mat4
@ mat4(m, v): v (4 floats) = m (4x4, column-major) * v, LEN 4 with scalar operands
mat4:
	vpush {d8-d15}
	mov r3, #0x03000000
	orr r3, r3, #0x30000
	vmsr fpscr, r3
	vldmia r0, {s16-s31}
	vldmia r1, {s0-s3}
	vmul.f32 s8, s16, s0
	vmla.f32 s8, s20, s1
	vmla.f32 s8, s24, s2
	vmla.f32 s8, s28, s3
	vstmia r1, {s8-s11}
	bic r3, r3, #0xff0000
	vmsr fpscr, r3
	vpop {d8-d15}
	bx lr
	.global gainloop
@ gainloop(dst, src, n8): dst[i] = src[i] * 0.5 + dst[i], 8 at a time, a loop inside the run
gainloop:
	vpush {d8-d15}
	vmrs r3, fpscr
	bic r3, r3, #0x370000
	orr r3, r3, #0x70000
	vmsr fpscr, r3
	vldr s0, half
1:	vldmia r1!, {s8-s15}
	vldmia r0, {s16-s23}
	vmla.f32 s16, s8, s0
	vstmia r0!, {s16-s23}
	subs r2, r2, #1
	bne 1b
	bic r3, r3, #0x370000
	vmsr fpscr, r3
	vpop {d8-d15}
	bx lr
half:	.float 0.5
	.section .note.GNU-stack,"",%progbits
