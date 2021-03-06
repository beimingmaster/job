package com.github.xsocket.job.parser;

import static com.github.xsocket.job.resume.Job51Resume.KEY_AGE;
import static com.github.xsocket.job.resume.Job51Resume.KEY_BIRTHDAY;
import static com.github.xsocket.job.resume.Job51Resume.KEY_JOB;
import static com.github.xsocket.job.resume.Job51Resume.KEY_NAME;
import static com.github.xsocket.job.resume.Job51Resume.KEY_SEX;
import static com.github.xsocket.job.resume.Job51Resume.KEY_WORDS;
import static com.github.xsocket.job.resume.Job51Resume.KEY_WORK_DURATION;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.xsocket.job.AbstractResumeParser;
import com.github.xsocket.job.Resume;
import com.github.xsocket.job.ResumeParser;
import com.github.xsocket.job.resume.Job51Resume;

public class Job51ResumeParser extends AbstractResumeParser implements ResumeParser {
  
  @Override
  public String getName() {
    return "前程无忧";
  }

  @Override
  public boolean canParse(File file) {
    if(file == null) {
      return false;
    } else {
      String name = file.getName();
      return name.contains("51job") || name.startsWith("前程无忧-");
    }
  }

  @Override
  public Resume parse(File file) throws Exception {    
    Document doc = parse2HtmlAsMail(file);
    if(doc == null) {
      doc = parse2Html(file);
    }
    Job51Resume resume = new Job51Resume();

    // 应聘人姓名
    // String name = doc.select("table table table tr span").first().text();
    // resume.set(KEY_NAME, name);
    String fileName = file.getName();
    if(fileName.startsWith("51job_")) {
      fileName = fileName.substring("51job_".length());
      resume.set(KEY_NAME, fileName.substring(0, fileName.indexOf("_")));
    } else {
      resume.set(KEY_NAME, fileName.substring(fileName.lastIndexOf("－") + 1, fileName.indexOf(".eml")));
    }
    
    // 应聘职位
    // 应聘职位：架构师（北京） 应聘公司：北京云途数字营销顾问有限公司 投递时间：2016-02-27 更新时间：2016-03-01
    String title = doc.select("table>tbody>tr>td").first().text();
    resume.set(KEY_JOB, intercept(title, "应聘职位：", "应聘公司："));
    
    // 个人信息
    // 1年工作经验 | 女 | 24岁(1991年11月 28日 )
    String detail = doc.select("table table table table span>b").first().text();
    String[] ds = detail.replaceAll("\\|", " ~!~ ").split("~!~");
    for(String str : ds) {
      if(str.contains("工作经验")) {
        int index = str.indexOf("年");
        if(index > 0) {
          resume.set(KEY_WORK_DURATION, str.substring(0, index));
        } else {
          resume.set(KEY_WORK_DURATION, str.substring(0, str.indexOf("工作经验")));
        }        
      } else if(str.contains("岁")) {
        int index = str.indexOf("岁");
        resume.set(KEY_AGE, str.substring(0, index));
        resume.set(KEY_BIRTHDAY, trim(str.substring(index + 1), "(",")","（","）"));
      } else if(str.contains("男")) {
        resume.set(KEY_SEX, "男");
      } else if(str.contains("女")) {
        resume.set(KEY_SEX, "女");
      }
      // 其他信息忽略
    }
    
    
    // 其他通用数据获取
    String currentKeyWord = null;
    Elements elems = doc.select("table table table td");
    for(Element elem : elems) {
      String text = elem.text();
      if(KEY_WORDS.contains(text)) {
        currentKeyWord = text;
      } else if(currentKeyWord != null) {
        resume.set(currentKeyWord, text);
        currentKeyWord = null;
      }
    }
    
    return resume;
  }
  
  protected Document parse2Html(File file) throws Exception {
    // 读取51job的简历文件
    List<String> lines = FileUtils.readLines(file);
    
    // 开始解析
    String state = "";
    String charset = null;
    StringBuilder sb = new StringBuilder();
    for(String line : lines) {
      if(line.startsWith("Content-Type:text/html;charset=")) {
        charset = line.substring("Content-Type:text/html;charset=".length()).replaceAll("\"", "");
        state = "ready";
      } else if(line.startsWith("Content-Type: text/html;charset=")) {
        charset = line.substring("Content-Type: text/html;charset=".length()).replaceAll("\"", "");
        state = "ready";
      } else if(state.equals("ready") && line.length() == 76) {
        state = "go";
        sb.append(line);
      } else if(state.equals("go") && line.length() > 0) {
        sb.append(line);
      } else if(state.equals("go") && line.length() == 0) {
        state = "over";
        break;
      }
    }
    
    // 未成功解析则返回null
    if(!"over".equals(state)) {
      return null;
    }
    
    // 解析成功, 开始进行数据读取
    String html = new String(Base64.decodeBase64(sb.toString()), charset);
    return Jsoup.parse(html);
  }
  
  protected Document parse2HtmlAsMail(File file) throws Exception {
    InputStream in = new FileInputStream(file);

    Session mailSession = Session.getDefaultInstance(System.getProperties(), null);

    MimeMessage msg = new MimeMessage(mailSession, in);
    
    Multipart part = (Multipart) msg.getContent();
    String html = null;
    for(int i = 0; i < part.getCount(); i++) {
      html = parseHtml(part.getBodyPart(i));
      if(html != null) {
        break;
      }
    }
    in.close();
    return html == null ? null : Jsoup.parse(html);
  }
  
  private String parseHtml(BodyPart body) throws Exception {
    //System.err.println(body.getContentType());
    if(body.getContentType().startsWith("text/html")) {
      Object content = body.getContent();
      return content == null ? null : content.toString();
    } else if(body.getContentType().startsWith("multipart")) {
      Multipart subpart = (Multipart) body.getContent();
      for(int j = 0; j < subpart.getCount(); j++) {
        BodyPart subbody = subpart.getBodyPart(j);
        String html = parseHtml(subbody);
        if(html != null) {
          return html;
        }
      }
    }
    return null;
  }
  

}
