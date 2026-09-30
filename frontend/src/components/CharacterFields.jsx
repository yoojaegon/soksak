import { useRef, useState } from 'react'
import { GENRES } from '../genres.js'
import CharacterImage from './CharacterImage.jsx'
import { api } from '../api.js'
import { resizeImage } from '../image.js'

// 캐릭터 기본정보 입력 필드들 (이름·소개·이미지·페르소나·첫 인사말·장르).
// 폼 상태는 부모가 들고, 여기서는 보여주기만 하는 controlled 컴포넌트.
// onToggleTag(value): 장르 칩 토글(부모가 form.tags 배열을 갱신).
// onSetField(name, value): 입력 이벤트 없이 값을 채워야 하는 필드용(업로드 결과 주소).
export default function CharacterFields({ form, onChange, onToggleTag, onSetField, errors = {} }) {
  const tags = form.tags ?? []
  const fileRef = useRef(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState('')

  // 파일을 고르면 곧바로 업로드하고, 돌려받은 주소를 폼의 imageUrl에 넣는다.
  // 캐릭터 저장과 분리돼 있어서, 저장을 취소하면 올라간 파일은 서버에 남는다.
  const pickImage = async (e) => {
    const file = e.target.files?.[0]
    // 같은 파일을 다시 골랐을 때도 change가 뜨도록 값을 비운다.
    e.target.value = ''
    if (!file) return
    setUploadError('')
    setUploading(true)
    try {
      const { url } = await api.uploadImage(await resizeImage(file))
      onSetField?.('imageUrl', url)
    } catch (err) {
      setUploadError(err.message || '이미지를 올리지 못했습니다.')
    } finally {
      setUploading(false)
    }
  }

  return (
    <>
      <label>
        <span className="field-caption">이름 <span className="req">*</span></span>
        <input name="name" value={form.name} onChange={onChange} placeholder="예: 하루" />
        {errors.name && <p className="field-error">! {errors.name}</p>}
      </label>
      <label>
        <span className="field-caption">소개 (선택)</span>
        <input
          name="description"
          value={form.description}
          onChange={onChange}
          placeholder="목록에 보일 한 줄 소개"
        />
      </label>
      <div className="field-block">
        <span className="field-caption">이미지 (선택)</span>
        <div className="image-field">
          <div className="image-preview" aria-hidden="true">
            <CharacterImage src={form.imageUrl}>
              <span className="image-preview-mono">{form.name?.[0] ?? '?'}</span>
            </CharacterImage>
          </div>
          <div className="image-field-input">
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={pickImage}
              hidden
            />
            <div className="image-actions">
              <button type="button" onClick={() => fileRef.current?.click()} disabled={uploading}>
                {uploading ? '올리는 중…' : form.imageUrl ? '이미지 변경' : '이미지 선택'}
              </button>
              {form.imageUrl && !uploading && (
                <button type="button" className="link-btn" onClick={() => onSetField?.('imageUrl', '')}>
                  제거
                </button>
              )}
            </div>
            {uploadError ? (
              <p className="field-error">! {uploadError}</p>
            ) : (
              <p className="field-hint">jpg·png·webp, 5MB까지. 없으면 이름 첫 글자로 보여요.</p>
            )}
          </div>
        </div>
      </div>
      <label>
        <span className="field-caption">페르소나 <span className="req">*</span> (성격·말투)</span>
        <textarea
          name="persona"
          value={form.persona}
          onChange={onChange}
          rows={5}
          placeholder="예: 너는 항상 밝고 친근하게 반말로 대답하는 친구야."
        />
        {errors.persona && <p className="field-error">! {errors.persona}</p>}
      </label>
      <label>
        <span className="field-caption">첫 인사말 <span className="req">*</span> (새 대화를 열 때 캐릭터가 먼저 건네는 말)</span>
        <textarea
          name="greeting"
          value={form.greeting}
          onChange={onChange}
          rows={3}
          placeholder="예: 안녕! 드디어 만났네. 오늘은 무슨 얘기를 해볼까?"
        />
        {errors.greeting && <p className="field-error">! {errors.greeting}</p>}
      </label>
      <div className="field-block">
        <span className="field-caption">장르 <span className="req">*</span> (하나 이상)</span>
        <div className="chip-picker">
          {GENRES.map((g) => (
            <button
              key={g.value}
              type="button"
              className={`chip${tags.includes(g.value) ? ' active' : ''}`}
              aria-pressed={tags.includes(g.value)}
              onClick={() => onToggleTag?.(g.value)}
            >
              {g.label}
            </button>
          ))}
        </div>
        {errors.tags && <p className="field-error">! {errors.tags}</p>}
      </div>
    </>
  )
}
