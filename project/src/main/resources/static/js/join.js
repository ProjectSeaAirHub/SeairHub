// [✅ /static/js/join.js 파일 전체를 이 최종 코드로 교체해주세요]

// 각 입력 항목의 유효성 검사 통과 여부를 저장하는 변수
let idCheck = false;
let emailCheck = false;
let pwdCheck = false;
let phoneCheck = false;
// [✅ 핵심 수정 1] 새로운 필수 항목들의 상태 변수 추가
let nameCheck = false;
let companyNameCheck = false;
let businessNumCheck = false;

$(function() {
    if ($('#email').is('[readonly]')) {
        emailCheck = true;
        $('#confirmEmail').hide();
    }
    
    $('#userId').on('keyup', confirmId);
    $('#email').on('keyup', confirmEmail);
    $('#userPwd, #userPwdConfirm').on('keyup', confirmPassword);
    
    $('#userName').on('keyup', function() {
        nameCheck = $(this).val().trim() !== '';
        updateJoinButton();
    });
    
    $('#phoneNum').on('input', function() {
	    var sanitized = $(this).val().replace(/[^0-9]/g, '');
	    $(this).val(sanitized);
        phoneCheck = (sanitized.length >= 10 && sanitized.length <= 11);
        updateJoinButton();
	});
	
    // [✅ 핵심 수정 2] 회사 이름, 사업자 번호 입력 감지 핸들러 추가
    $('#companyName').on('keyup', function() {
        companyNameCheck = $(this).val().trim() !== '';
        updateJoinButton();
    });

    $('#businessNum').on('keyup', function() {
        businessNumCheck = $(this).val().trim() !== '';
        updateJoinButton();
    });

    $('#businessLicenseFile').on('change', function() {
        if (this.files.length > 0) {
            $('.file-display').val(this.files[0].name);
        } else {
            $('.file-display').val('선택된 파일 없음');
        }
    });
	
    $('#joinBtn').on('click', function() {
        if (isAllCheck()) {
            $('#joinForm').submit();
        } else {
            alert('필수 입력 정보를 모두 기입해주세요.');
        }
    });

    updateJoinButton();
});


function isAllCheck() {
    // [✅ 핵심 수정 3] 모든 필수 항목 변수를 조건에 추가
    return idCheck && emailCheck && pwdCheck && nameCheck && phoneCheck && companyNameCheck && businessNumCheck;
}

function updateJoinButton() {
    if (isAllCheck()) {
        $('#joinBtn').prop('disabled', false).css({
            'background-color': 'var(--primary-color)', 'cursor': 'pointer', 'opacity': '1'
        });
    } else {
        $('#joinBtn').prop('disabled', true).css({
            'background-color': '#ccc', 'cursor': 'not-allowed', 'opacity': '0.6'
        });
    }
}

function confirmId() {
    let userId = $('#userId').val();
    if (userId.trim().length < 5 || userId.trim().length > 10) {
        $('#confirmId').css('color', 'red').text('아이디는 5~10자로 입력해주세요.'); idCheck = false; updateJoinButton(); return;
    }
    $.ajax({
        url: '/user/confirmId', method: 'POST', data: { "userId": userId },
        success: function(isAvailable) {
            if (isAvailable) { $('#confirmId').css('color', 'blue').text('사용 가능한 아이디입니다.'); idCheck = true;
            } else { $('#confirmId').css('color', 'red').text('이미 사용 중인 아이디입니다.'); idCheck = false; }
            updateJoinButton();
        },
        error: function() { $('#confirmId').css('color', 'red').text('오류가 발생했습니다.'); idCheck = false; updateJoinButton(); }
    });
}
function confirmEmail() {
    if ($('#email').is('[readonly]')) { return; }
    let email = $('#email').val(); let emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
        $('#confirmEmail').css('color', 'red').text('올바른 이메일 형식이 아닙니다.'); emailCheck = false; updateJoinButton(); return;
    }
    $.ajax({
        url: '/user/confirmEmail', method: 'POST', data: { "email": email },
        success: function(isAvailable) {
            if (isAvailable) { $('#confirmEmail').css('color', 'blue').text('사용 가능한 이메일입니다.'); emailCheck = true;
            } else { $('#confirmEmail').css('color', 'red').text('이미 사용 중인 이메일입니다.'); emailCheck = false; }
            updateJoinButton();
        },
        error: function() { $('#confirmEmail').css('color', 'red').text('오류가 발생했습니다.'); emailCheck = false; updateJoinButton(); }
    });
}
function confirmPassword() {
    let userPwd = $('#userPwd').val(); let userPwdConfirm = $('#userPwdConfirm').val();
    if (userPwd.length < 8) {
        $('#confirmPwd').css('color', 'red').text('비밀번호는 8자 이상이어야 합니다.'); pwdCheck = false;
    } else if (userPwd === userPwdConfirm) {
        $('#confirmPwd').css('color', 'blue').text('비밀번호가 일치합니다.'); pwdCheck = true;
    } else {
        $('#confirmPwd').css('color', 'red').text('비밀번호가 일치하지 않습니다.'); pwdCheck = false;
    }
    updateJoinButton();
}