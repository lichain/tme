#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <pthread.h>
#include <string.h>
#include <netinet/in.h>
#include <unistd.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <iostream>
#include <netinet/tcp.h>
#include <openssl/md5.h>
#include "MistMessage.pb.h"
#include "GateTalk.pb.h"
#include "SpnMessage.pb.h"
using namespace com::trendmicro::spn::proto;
using namespace com::trendmicro::mist::proto;

#define MIST_PORT 9498

typedef struct{
    int isSink;
    uint32_t size;
    char exName[100];
    int count;
    int idx;
    unsigned int interval;
    int sink_type;
}param;

int error=0;
int pros=0;
time_t start=0;

int connectTo(int port){
    int sock=socket(AF_INET,SOCK_STREAM,0);
    struct sockaddr_in sa;
    sa.sin_family=AF_INET;
    inet_aton("127.0.0.1",(struct in_addr *)&sa.sin_addr.s_addr);
    sa.sin_port=htons(port);

    if(connect(sock,(struct sockaddr*)&sa,sizeof(sa))<0){
        close(sock);
        return -1;
    }
    else
        return sock;
}

int sendRequest(const Command& req, Command& res){
    int sock;
    if((sock=connectTo(MIST_PORT))<0)
        return -1;

    uint32_t byteSize=htonl(req.ByteSize());
    write(sock,&byteSize,4);
    req.SerializeToFileDescriptor(sock);

    read(sock,&byteSize,4);
    if(res.ParseFromFileDescriptor(sock)){
        close(sock);
        return 0;
    }
    else{
        close(sock);
        return -1;
    }
}

int createSession(){
    int session_id=-1;

    Command cmd;
    Session* sessPtr=cmd.add_session();
    sessPtr->mutable_connection()->set_host_name("");
    sessPtr->mutable_connection()->set_host_port("");
    sessPtr->mutable_connection()->set_username("");
    sessPtr->mutable_connection()->set_password("");
    sessPtr->mutable_connection()->set_broker_type("");

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            session_id=atoi(res.response(0).context().c_str());

    return session_id;
}

int mountExchange(int isSink, int sessId, const char* exName){
    int result=-1;
    int isTopic=(strncmp(exName,"topic:",6)==0);
    printf("DEBUG: isTopic=%d\n",isTopic);
    Command cmd;
    Client* clientPtr=cmd.add_client();
    clientPtr->set_session_id(sessId);    
    clientPtr->mutable_channel()->set_name(isTopic?&exName[6]:exName);
    clientPtr->mutable_channel()->set_type(isTopic?Channel::TOPIC:Channel::QUEUE);
    clientPtr->set_type(isSink?Client::PRODUCER:Client::CONSUMER);
    clientPtr->set_action(Client::MOUNT);
        
    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            result=0;
            
    printf("DEBUG: result=%d\n",result);
    return result;
}

int destroySession(int sessId){
    int result=-1;
    Command cmd;
    Request* reqPtr=cmd.add_request();
    reqPtr->set_type(Request::SESSION_DESTROY);
    char buf[20];
    snprintf(buf,20,"%d",sessId);
    reqPtr->set_argument(buf);

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            result=0;
    return result;
}

int attachSession(int isSink,int sessId){
    int sock=-1;
    Command cmd;
    Command res;
    Request* reqPtr=cmd.add_request();
    reqPtr->set_type(Request::CLIENT_ATTACH);
    char buf[20];
    snprintf(buf,20,"%d",sessId);
    reqPtr->set_argument(buf);
    reqPtr->set_role(isSink?Request::SINK:Request::SOURCE);

    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            sock=connectTo(atoi(res.response(0).context().c_str()));
    int trueflag=1;
    setsockopt(sock,IPPROTO_TCP,TCP_NODELAY,&trueflag,sizeof(int));
    return sock;
}

int detachSession(int isSink,int sessId){
    int result=-1;
    Command cmd;
    Request* reqPtr=cmd.add_request();
    reqPtr->set_type(Request::CLIENT_DETACH);
    char buf[20];
    snprintf(buf,20,"%d",sessId);
    reqPtr->set_argument(buf);
    reqPtr->set_role(isSink?Request::SINK:Request::SOURCE);

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            result=0;
    return result;
}

void* producer(void* p){
    param prm=*(param*)p;
    fprintf(stderr,"producer %d: created\n",prm.idx);
    uint32_t numElem=prm.size>>2;
    int sessid=createSession();
    if(sessid<0){
        error=1;
        pthread_exit(NULL);
    }
    fprintf(stderr,"producer %d: session %d created\n",prm.idx,sessid);

    char fnbuf[100];
    sprintf(fnbuf,"t_socket_sink.%s.out.%d",prm.exName,prm.idx);
    FILE* fp=fopen(fnbuf,"w");

    if(mountExchange(prm.isSink,sessid,prm.exName)!=0){
        error=1;
        pthread_exit(NULL);
    }
    fprintf(stderr,"producer %d: session %d mounted\n",prm.idx,sessid);

    int sock=attachSession(prm.isSink,sessid);
    fprintf(stderr,"producer %d: session %d attached\n",prm.idx,sessid);
    if(sock<0){
        error=1;
        pthread_exit(NULL);
    }

    start=time(NULL);

    uint32_t i;
    uint32_t realSize=numElem<<2;
    uint32_t* buf=(uint32_t*)malloc(realSize+100);
    int startIdx=prm.count/pros*prm.idx;
    int endIdx=(prm.idx==pros-1?prm.count:prm.count/pros*(prm.idx+1));

    unsigned char md5[MD5_DIGEST_LENGTH];

    char* buf2=NULL;
    for(i=startIdx;i<endIdx;i++){
        buf[0]=i;
        unsigned int state=i;
        int j;
        for(j=1;j<=numElem-1;j++)
           buf[j]=rand_r(&state);

        Container container;
        container.mutable_container_base()->mutable_message_list()->add_messages()->set_derived(buf,realSize);
        container.mutable_container_base()->mutable_message_list()->mutable_messages(0)->mutable_msg_base()->set_subject("");
        if(buf2==NULL)
            buf2=(char*)malloc(container.ByteSize());
        container.SerializeToArray(buf2,container.ByteSize());
        
        MD5((unsigned char*)buf2,container.ByteSize(),md5);
        int k;
        for(int k=0;k<MD5_DIGEST_LENGTH;k++)
            fprintf(fp,"%02x",md5[k]);
        fprintf(fp,"\n");
        fflush(fp);

        MessageBlock msg;
        msg.set_id(prm.exName);
        msg.set_message(buf2,container.ByteSize());
            
        uint32_t byteSize=htonl(msg.ByteSize());
        write(sock,&byteSize,4);
        msg.SerializeToFileDescriptor(sock);

        read(sock,&byteSize,4);
        read(sock,buf,ntohl(byteSize));
        Response res;
        res.ParseFromArray(buf,ntohl(byteSize));
        if(!res.success()){
            fprintf(stderr,"producer error!\n");
            error=1;
            break;
        }
        if(prm.interval>0)
            usleep(prm.interval*1000);
        
        printf("DEBUG: prm.sink_type: %d\n",prm.sink_type);
        
        // recreate session
        if(1==prm.sink_type){
            
            printf("DEBUG: destroying session...\n");
            close(sock);
            fclose(fp);
            detachSession(prm.isSink,sessid);
            destroySession(sessid);
            
            printf("DEBUG: creating session...\n");
            sessid=createSession();
            if(sessid<0){
                error=1;
                pthread_exit(NULL);
            }
            fprintf(stderr,"producer %d: session %d created\n",prm.idx,sessid);

            if(mountExchange(prm.isSink,sessid,prm.exName)!=0){
                error=1;
                pthread_exit(NULL);
            }
            fprintf(stderr,"producer %d: session %d mounted\n",prm.idx,sessid);
            sock=attachSession(prm.isSink,sessid);
            fprintf(stderr,"producer %d: session %d attached\n",prm.idx,sessid);
            if(sock<0){
                error=1;
                pthread_exit(NULL);
            }
            sprintf(fnbuf,"t_socket_sink.%s.out.%d",prm.exName,prm.idx);
            fp=fopen(fnbuf,"a");
        }
        // reattach session
        else if(2==prm.sink_type){
        
            printf("DEBUG: detaching session...\n");
            close(sock);
            fclose(fp);
            detachSession(prm.isSink,sessid);
            
            printf("DEBUG: attaching session...\n");
            sock=attachSession(prm.isSink,sessid);
            fprintf(stderr,"producer %d: session %d attached\n",prm.idx,sessid);
            if(sock<0){
                error=1;
                pthread_exit(NULL);
            }
            sprintf(fnbuf,"t_socket_sink.%s.out.%d",prm.exName,prm.idx);
            fp=fopen(fnbuf,"a");
        }
    }

    close(sock);
    fclose(fp);
    destroySession(sessid);
    fprintf(stderr,"producer #%d finished sending\n",prm.idx);
    pthread_exit(NULL);
    return NULL;
}

int read_all(int blocking_fd, char* buf, size_t size)
{
    size_t size_read = 0;
    while(size_read < size)
    {
        int n = read(blocking_fd, buf + size_read, size - size_read);
        if(n <= 0)
            return n;
        size_read += n;
    }
    
    return size_read;
}

int main(int argc, char* argv[]){
    if(argc<7){
        fprintf(stderr,"\nusage: %s [1.msg size] [2.total msg count] [3.producer count] [4.exchange name] [5.interval] [6.sink type]\n\n", argv[0]);
        fprintf(stderr,"\t[4.exchange name=topic:xxx if it's a topic, default is queue]\n");
        fprintf(stderr,"\t[6.sink type=0:no-reattach|1:re-create session|2:re-attach session]\n");
        exit(-1);
    }
    int size=atoi(argv[1]);
    int count=atoi(argv[2]);
    pros=atoi(argv[3]);
    int interval=atoi(argv[5]);
    int sink_type=atoi(argv[6]);
    fprintf(stderr,"program started\n");
    
    printf("DEBUG: sink_type: %d\n",sink_type);
    
    int i;
    pthread_t* th_proAry=(pthread_t*)malloc(sizeof(pthread_t)*pros);
    for(i=0;i<pros;i++){
        param* proprm=(param*)malloc(sizeof(param));
        proprm->isSink=1;
        proprm->size=size;
        strcpy(proprm->exName,argv[4]);
        proprm->count=count;
        proprm->idx=i;
        proprm->interval=interval;
        proprm->sink_type=sink_type;
        pthread_create(&th_proAry[i],NULL,producer,(void*)proprm);
    }

    int done=0;
    int progress=0;
    
    for(i=0;i<pros;i++)
        pthread_join(th_proAry[i],NULL);

    long totalSec=time(NULL)-start;

    fprintf(stderr,"send complete\n\n");
    
    if(error && (!done))
        exit(-1);
    else
        exit(0);
}
