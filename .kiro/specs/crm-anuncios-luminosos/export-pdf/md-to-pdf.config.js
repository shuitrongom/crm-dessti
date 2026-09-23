module.exports = {
  stylesheet: ['style.css'],
  pdf_options: {
    format: 'Letter',
    margin: { top: '16mm', bottom: '16mm', left: '15mm', right: '15mm' },
    printBackground: true,
    displayHeaderFooter: true,
    headerTemplate: '<div style=""font-size:8px;color:#94a3b8;width:100%;text-align:right;padding:4px 15px;"">CRM Anuncios Luminosos — Diseño Técnico</div>',
    footerTemplate: '<div style=""font-size:8px;color:#94a3b8;width:100%;text-align:center;padding:4px 0;"">Página <span class=""pageNumber""></span> de <span class=""totalPages""></span></div>'
  },
  launch_options: { args: ['--no-sandbox','--disable-setuid-sandbox'] },
  body_class: 'markdown-body'
};