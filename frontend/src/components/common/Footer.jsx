const FOOTER_LINKS = ['이용약관', '개인정보처리방침', '고객 문의']

function Footer() {
  return (
    <footer className="border-t border-gray-200">
      <div className="mx-auto flex max-w-[1200px] flex-wrap gap-6 px-6 py-8 text-sm text-gray-600">
        <span className="font-bold text-gray-900">보금</span>
        {FOOTER_LINKS.map((label) => (
          <a key={label} href="#" className="hover:text-gray-900">
            {label}
          </a>
        ))}
      </div>
    </footer>
  )
}

export default Footer
